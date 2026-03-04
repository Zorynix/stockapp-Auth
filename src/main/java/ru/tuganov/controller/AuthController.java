package ru.tuganov.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.tuganov.dto.auth.*;
import ru.tuganov.service.AppUserService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AppUserService appUserService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = appUserService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verify(@Valid @RequestBody VerifyEmailRequest request) {
        AuthResponse response = appUserService.verifyEmail(request.email(), request.code());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = appUserService.loginByEmail(request.email(), request.password());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/telegram")
    public ResponseEntity<AuthResponse> telegramAuth(@Valid @RequestBody TelegramAuthRequest request) {
        AuthResponse response = appUserService.loginByTelegram(request.initData());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-code")
    public ResponseEntity<Void> resendCode(@Valid @RequestBody ResendCodeRequest request) {
        appUserService.resendVerificationCode(request.email());
        return ResponseEntity.ok().build();
    }
}
