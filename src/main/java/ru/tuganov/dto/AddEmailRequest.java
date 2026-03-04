package ru.tuganov.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AddEmailRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
