package ru.tuganov.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkTelegramRequest(
        @NotBlank String initData
) {}
