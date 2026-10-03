package com.paytm.reservation.auth;

import com.paytm.reservation.auth.dto.LoginRequest;
import com.paytm.reservation.auth.dto.LoginResponse;
import com.paytm.reservation.auth.dto.RegisterRequest;
import com.paytm.reservation.auth.dto.RegisterResponse;
import com.paytm.reservation.common.exception.DuplicateUserException;
import com.paytm.reservation.common.exception.InvalidCredentialsException;
import com.paytm.reservation.common.security.JwtService;
import com.paytm.reservation.user.Role;
import com.paytm.reservation.user.User;
import com.paytm.reservation.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByExternalUserId(request.username())) {
            throw new DuplicateUserException(request.username());
        }
        String hash = passwordEncoder.encode(request.password());
        User user = userRepository.insert(request.username(), Role.USER, hash);
        return new RegisterResponse(user.id(), user.externalUserId(), user.role());
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByExternalUserId(request.username())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        String token = jwtService.generateAccessToken(user);
        return LoginResponse.bearer(token, jwtService.getExpirationSeconds());
    }
}
