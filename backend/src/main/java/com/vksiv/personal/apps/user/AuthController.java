package com.vksiv.personal.apps.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vksiv.personal.apps.security.SecurityPrincipal;
import com.vksiv.personal.apps.user.AuthDtos.AuthResponse;
import com.vksiv.personal.apps.user.AuthDtos.LoginRequest;
import com.vksiv.personal.apps.user.AuthDtos.RefreshRequest;
import com.vksiv.personal.apps.user.AuthDtos.RegisterRequest;
import com.vksiv.personal.apps.user.AuthDtos.UserResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/auth/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** Requires a valid access token; used by the UI to restore a session on reload. */
    @GetMapping("/me")
    public UserResponse me() {
        return authService.currentUser(SecurityPrincipal.current().id());
    }
}
