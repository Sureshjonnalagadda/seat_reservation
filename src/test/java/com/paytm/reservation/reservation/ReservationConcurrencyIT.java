package com.paytm.reservation.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.reservation.SeatReservationApplication;
import com.paytm.reservation.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = SeatReservationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ReservationConcurrencyIT {

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("seat_reservation")
            .withUsername("test")
            .withPassword("test")
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        baseUrl = "http://localhost:" + port;
        adminToken = IntegrationTestSupport.adminToken(restTemplate, baseUrl, objectMapper);
    }

    @Test
    void hotSeat_allowsOnlyOneConfirmation() throws Exception {
        long showId = IntegrationTestSupport.createShow(
                restTemplate,
                baseUrl,
                adminToken,
                objectMapper,
                List.of("A12")
        );
        int workers = 30;
        List<String> userTokens = new ArrayList<>(workers);
        for (int i = 0; i < workers; i++) {
            userTokens.add(IntegrationTestSupport.registerUser(
                    restTemplate,
                    baseUrl,
                    "hot-" + i + "-" + UUID.randomUUID().toString().substring(0, 8)
            ));
        }

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            int index = i;
            String key = "hot-seat-" + index;
            String token = userTokens.get(index);
            futures.add(pool.submit(() -> IntegrationTestSupport.reserveStatusCode(
                    baseUrl,
                    token,
                    showId,
                    List.of("A12"),
                    key
            )));
        }
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.MINUTES);

        Map<Integer, Integer> statusCounts = new HashMap<>();
        for (Future<Integer> future : futures) {
            int code = future.get();
            statusCounts.merge(code, 1, Integer::sum);
        }

        assertThat(statusCounts).as("hot-seat status histogram").doesNotContainKey(401);
        assertThat(statusCounts.keySet().stream().noneMatch(c -> c >= 500)).isTrue();
        assertThat(statusCounts.getOrDefault(201, 0)).isEqualTo(1);
        assertThat(statusCounts.keySet()).containsOnly(201, 409);
        assertThat(statusCounts.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(workers);
        assertThat(IntegrationTestSupport.seatStatus(restTemplate, baseUrl, showId, "A12")).isEqualTo("CONFIRMED");
    }

    @Test
    void idempotencyStorm_createsOneReservationAndReplays() throws Exception {
        long showId = IntegrationTestSupport.createShow(
                restTemplate,
                baseUrl,
                adminToken,
                objectMapper,
                List.of("B1", "B2")
        );
        String userToken = IntegrationTestSupport.registerUser(
                restTemplate,
                baseUrl,
                "idem-" + UUID.randomUUID().toString().substring(0, 8)
        );
        String sharedKey = UUID.randomUUID().toString();
        int workers = 50;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            futures.add(pool.submit(() -> IntegrationTestSupport.reserveStatusCode(
                    baseUrl,
                    userToken,
                    showId,
                    List.of("B1"),
                    sharedKey
            )));
        }
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.MINUTES);

        Map<Integer, Integer> statusCounts = new HashMap<>();
        for (Future<Integer> future : futures) {
            int code = future.get();
            statusCounts.merge(code, 1, Integer::sum);
        }

        assertThat(statusCounts).as("idempotency status histogram").doesNotContainKey(401);
        assertThat(statusCounts.keySet().stream().noneMatch(c -> c >= 500)).isTrue();
        assertThat(statusCounts.getOrDefault(201, 0)).isEqualTo(1);
        assertThat(statusCounts.keySet()).allMatch(c -> c == 201 || c == 200 || c == 409);
        assertThat(statusCounts.getOrDefault(200, 0) + statusCounts.getOrDefault(409, 0)).isEqualTo(workers - 1);
        assertThat(statusCounts.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(workers);
    }

    @Test
    void sameIdempotencyKeyWithDifferentSeats_returnsConflict() throws Exception {
        long showId = IntegrationTestSupport.createShow(
                restTemplate,
                baseUrl,
                adminToken,
                objectMapper,
                List.of("C1", "C2")
        );
        String userToken = IntegrationTestSupport.registerUser(
                restTemplate,
                baseUrl,
                "keyreuse-" + UUID.randomUUID().toString().substring(0, 8)
        );
        String key = UUID.randomUUID().toString();

        ResponseEntity<String> first = IntegrationTestSupport.reserve(
                restTemplate,
                baseUrl,
                userToken,
                showId,
                List.of("C1"),
                key
        );
        ResponseEntity<String> second = IntegrationTestSupport.reserve(
                restTemplate,
                baseUrl,
                userToken,
                showId,
                List.of("C2"),
                key
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void multiSeatRequest_isAllOrNothing() throws Exception {
        long showId = IntegrationTestSupport.createShow(
                restTemplate,
                baseUrl,
                adminToken,
                objectMapper,
                List.of("D1", "D2", "D3")
        );
        String userOne = IntegrationTestSupport.registerUser(
                restTemplate,
                baseUrl,
                "u1-" + UUID.randomUUID().toString().substring(0, 8)
        );
        IntegrationTestSupport.reserve(
                restTemplate,
                baseUrl,
                userOne,
                showId,
                List.of("D3"),
                UUID.randomUUID().toString()
        );

        String userTwo = IntegrationTestSupport.registerUser(
                restTemplate,
                baseUrl,
                "u2-" + UUID.randomUUID().toString().substring(0, 8)
        );
        ResponseEntity<String> response = IntegrationTestSupport.reserve(
                restTemplate,
                baseUrl,
                userTwo,
                showId,
                List.of("D1", "D2", "D3"),
                UUID.randomUUID().toString()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(IntegrationTestSupport.seatStatus(restTemplate, baseUrl, showId, "D1")).isEqualTo("AVAILABLE");
        assertThat(IntegrationTestSupport.seatStatus(restTemplate, baseUrl, showId, "D2")).isEqualTo("AVAILABLE");
        assertThat(IntegrationTestSupport.seatStatus(restTemplate, baseUrl, showId, "D3")).isEqualTo("CONFIRMED");
    }

    @Test
    void cancellation_releasesSeats() throws Exception {
        long showId = IntegrationTestSupport.createShow(
                restTemplate,
                baseUrl,
                adminToken,
                objectMapper,
                List.of("E1", "E2")
        );
        String userToken = IntegrationTestSupport.registerUser(
                restTemplate,
                baseUrl,
                "cancel-" + UUID.randomUUID().toString().substring(0, 8)
        );
        ResponseEntity<String> reserveResponse = IntegrationTestSupport.reserve(
                restTemplate,
                baseUrl,
                userToken,
                showId,
                List.of("E1", "E2"),
                UUID.randomUUID().toString()
        );
        String reservationId = objectMapper.readTree(reserveResponse.getBody()).get("reservation_id").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        ResponseEntity<String> cancelResponse = restTemplate.exchange(
                baseUrl + "/reservations/" + reservationId + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(cancelResponse.getBody()).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(IntegrationTestSupport.seatStatus(restTemplate, baseUrl, showId, "E1")).isEqualTo("AVAILABLE");
        assertThat(IntegrationTestSupport.seatStatus(restTemplate, baseUrl, showId, "E2")).isEqualTo("AVAILABLE");
    }
}
