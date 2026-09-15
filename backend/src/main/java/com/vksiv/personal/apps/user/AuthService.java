package com.vksiv.personal.apps.user;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vksiv.personal.apps.common.ApiExceptions.EmailAlreadyUsedException;
import com.vksiv.personal.apps.common.ApiExceptions.InvalidCredentialsException;
import com.vksiv.personal.apps.common.ApiExceptions.NotFoundException;
import com.vksiv.personal.apps.security.JwtService;
import com.vksiv.personal.apps.user.AuthDtos.AuthResponse;
import com.vksiv.personal.apps.user.AuthDtos.LoginRequest;
import com.vksiv.personal.apps.user.AuthDtos.RegisterRequest;
import com.vksiv.personal.apps.user.AuthDtos.UserResponse;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

@Service
public class AuthService {

    /**
     * A valid BCrypt hash of a value nobody knows. Verifying against this when the
     * email is unknown keeps the response time for "no such user" similar to
     * "wrong password", so the endpoint does not leak which emails are registered.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalise(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyUsedException(email);
        }
        User user = users.save(new User(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim()));
        return issueFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(normalise(request.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw new InvalidCredentialsException("No account for that email");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Password mismatch for " + user.getEmail());
        }
        return issueFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidCredentialsException("Unusable refresh token: " + ex.getMessage());
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new InvalidCredentialsException("Token is not a refresh token");
        }
        User user = users.findById(UUID.fromString(claims.getSubject()))
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token refers to a deleted user"));
        return issueFor(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID id) {
        return users.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User no longer exists"));
    }

    private AuthResponse issueFor(User user) {
        return new AuthResponse(
                jwtService.issueAccessToken(user.getId(), user.getEmail()),
                jwtService.issueRefreshToken(user.getId(), user.getEmail()),
                jwtService.accessTokenSeconds(),
                UserResponse.from(user));
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase();
    }
}
