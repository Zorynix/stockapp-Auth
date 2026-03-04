package ru.tuganov.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.tuganov.dto.*;
import ru.tuganov.dto.auth.AuthResponse;
import ru.tuganov.security.AppUserDetails;
import ru.tuganov.service.AccountLinkingService;
import ru.tuganov.service.AppUserService;
import ru.tuganov.service.ProfileService;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final AccountLinkingService accountLinkingService;
    private final AppUserService appUserService;

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(
            @AuthenticationPrincipal AppUserDetails userDetails) {
        return ResponseEntity.ok(profileService.getProfile(userDetails.getUserId()));
    }

    @PatchMapping("/notifications")
    public ResponseEntity<ProfileResponse> updateNotifications(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @Valid @RequestBody UpdateNotificationsRequest request) {
        ProfileResponse response = profileService.updateEmailNotifications(
                userDetails.getUserId(), request.enabled());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal AppUserDetails userDetails) {
        profileService.deleteAccount(userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/link-telegram")
    public ResponseEntity<ProfileResponse> linkTelegram(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @Valid @RequestBody LinkTelegramRequest request) {
        ProfileResponse response = accountLinkingService.linkTelegram(
                userDetails.getUserId(), request.initData());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/link-telegram/resolve")
    public ResponseEntity<ProfileResponse> resolveConflict(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @Valid @RequestBody ResolveConflictRequest request) {
        ProfileResponse response = accountLinkingService.resolveConflict(
                userDetails.getUserId(), request.initData(), request.resolution());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/add-email")
    public ResponseEntity<AddEmailResponse> addEmail(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @Valid @RequestBody AddEmailRequest request) {
        AddEmailResponse response = appUserService.addEmail(
                userDetails.getUserId(), request.email(), request.password());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-add-email")
    public ResponseEntity<AuthResponse> verifyAddEmail(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @Valid @RequestBody VerifyAddEmailRequest request) {
        AuthResponse response = appUserService.verifyAddEmail(
                userDetails.getUserId(), request.email(), request.code(), request.resolution());
        return ResponseEntity.ok(response);
    }
}
