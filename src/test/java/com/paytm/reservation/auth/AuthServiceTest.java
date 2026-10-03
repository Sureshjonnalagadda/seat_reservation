package com.paytm.reservation.auth;

import com.paytm.reservation.auth.dto.LoginRequest;
import com.paytm.reservation.auth.dto.RegisterRequest;
import com.paytm.reservation.common.exception.DuplicateUserException;
import com.paytm.reservation.common.exception.InvalidCredentialsException;
import com.paytm.reservation.common.security.JwtService;
import com.paytm.reservation.user.Role;
import com.paytm.reservation.user.User;
import com.paytm.reservation.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerCreatesUser() {
        when(userRepository.existsByExternalUserId("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.insert(eq("alice"), eq(Role.USER), eq("hash")))
                .thenReturn(new User(1L, "alice", Role.USER, "hash", Instant.now(), Instant.now()));

        var response = authService.register(new RegisterRequest("alice", "password123"));

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.role()).isEqualTo(Role.USER);
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByExternalUserId("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "password123")))
                .isInstanceOf(DuplicateUserException.class);
    }

    @Test
    void loginReturnsToken() {
        User user = new User(1L, "alice", Role.USER, "hash", Instant.now(), Instant.now());
        when(userRepository.findByExternalUserId("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        var response = authService.login(new LoginRequest("alice", "password123"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User(1L, "alice", Role.USER, "hash", Instant.now(), Instant.now());
        when(userRepository.findByExternalUserId("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), eq("hash"))).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
