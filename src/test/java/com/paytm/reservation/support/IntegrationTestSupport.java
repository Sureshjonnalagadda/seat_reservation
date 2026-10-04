package com.paytm.reservation.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class IntegrationTestSupport {

    private IntegrationTestSupport() {
    }

    public static String registerUser(TestRestTemplate rest, String baseUrl, String username) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        rest.postForEntity(
                baseUrl + "/auth/register",
                Map.of("username", username, "password", "password123"),
                String.class
        );
        ResponseEntity<String> login = rest.postForEntity(
                baseUrl + "/auth/login",
                Map.of("username", username, "password", "password123"),
                String.class
        );
        if (!login.getStatusCode().is2xxSuccessful() || login.getBody() == null) {
            throw new IllegalStateException("Login failed for " + username + ": " + login.getStatusCode());
        }
        return mapper.readTree(login.getBody()).get("access_token").asText();
    }

    public static String adminToken(TestRestTemplate rest, String baseUrl, ObjectMapper mapper) throws Exception {
        ResponseEntity<String> login = rest.postForEntity(
                baseUrl + "/auth/login",
                Map.of("username", "admin", "password", "adminrole"),
                String.class
        );
        return mapper.readTree(login.getBody()).get("access_token").asText();
    }

    public static long createShow(
            TestRestTemplate rest,
            String baseUrl,
            String adminToken,
            ObjectMapper mapper,
            List<String> seats
    ) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);
        String body = mapper.writeValueAsString(Map.of(
                "name", "Integration Show " + UUID.randomUUID(),
                "seats", seats,
                "price_paise", 10_000,
                "per_user_limit", 4
        ));
        ResponseEntity<String> response = rest.exchange(
                baseUrl + "/shows",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
        return mapper.readTree(response.getBody()).get("id").asLong();
    }

    /** Thread-safe reserve for concurrent load tests (dedicated client per call). */
    public static int reserveStatusCode(
            String baseUrl,
            String userToken,
            long showId,
            List<String> seats,
            String idempotencyKey
    ) throws Exception {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        RestTemplate client = new RestTemplate(requestFactory);
        client.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            protected boolean hasError(HttpStatusCode statusCode) {
                return statusCode.is5xxServerError();
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(userToken);
        headers.set("Idempotency-Key", idempotencyKey);
        headers.setConnection("close");
        String body = new ObjectMapper().writeValueAsString(Map.of("seats", seats));
        ResponseEntity<String> response = client.exchange(
                baseUrl + "/shows/" + showId + "/reserve",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
        return response.getStatusCode().value();
    }

    public static ResponseEntity<String> reserve(
            TestRestTemplate rest,
            String baseUrl,
            String userToken,
            long showId,
            List<String> seats,
            String idempotencyKey
    ) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(userToken);
        headers.set("Idempotency-Key", idempotencyKey);
        String body = new ObjectMapper().writeValueAsString(Map.of("seats", seats));
        return rest.exchange(
                baseUrl + "/shows/" + showId + "/reserve",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
    }

    public static String seatStatus(TestRestTemplate rest, String baseUrl, long showId, String seatNumber)
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ResponseEntity<String> response = rest.getForEntity(baseUrl + "/shows/" + showId, String.class);
        JsonNode seats = mapper.readTree(response.getBody()).get("seats");
        for (JsonNode seat : seats) {
            if (seat.get("seat_number").asText().equals(seatNumber)) {
                return seat.get("status").asText();
            }
        }
        throw new IllegalStateException("Seat not found: " + seatNumber);
    }
}
