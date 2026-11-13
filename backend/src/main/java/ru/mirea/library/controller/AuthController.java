package ru.mirea.library.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import ru.mirea.library.dto.*;
import ru.mirea.library.security.*;
import ru.mirea.library.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final LoginRateLimiter limiter;

    public AuthController(AuthService auth, LoginRateLimiter limiter) {
        this.auth = auth;
        this.limiter = limiter;
    }

    @PostMapping("/login")
    @SecurityRequirements
    public TokenResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        limiter.check(request.getRemoteAddr());
        return auth.login(body);
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest body) {
        return auth.refresh(body);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    public void logout(@Valid @RequestBody RefreshRequest body) {
        auth.logout(body);
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal CurrentUser user) {
        return auth.me(user);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody PasswordChangeRequest body) {
        auth.changePassword(user, body);
    }
}
