package com.example.lms.dto;

import jakarta.validation.constraints.NotBlank;

/** Destructive operation requires both password verification and typing the username. */
public record AccountDeletionRequest(
        @NotBlank(message = "Enter your current password.") String currentPassword,
        @NotBlank(message = "Type your username to confirm account deletion.") String confirmationUsername) {
}
