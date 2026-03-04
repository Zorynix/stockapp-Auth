package ru.tuganov.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import ru.tuganov.dto.ResolveConflictRequest.ConflictResolution;

public record VerifyAddEmailRequest(
        @NotBlank @Email String email,
        @NotBlank String code,
        ConflictResolution resolution
) {}
