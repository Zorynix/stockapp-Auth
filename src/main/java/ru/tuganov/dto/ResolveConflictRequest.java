package ru.tuganov.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResolveConflictRequest(
        @NotBlank String initData,
        @NotNull ConflictResolution resolution
) {
    public enum ConflictResolution {
        KEEP_WEB, KEEP_TELEGRAM, MERGE
    }
}
