package com.example.lms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "Enter your current password.")
        String currentPassword,
        @NotBlank(message = "Enter a new password.")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters.")
        String newPassword,
        @NotBlank(message = "Confirm your new password.")
        String confirmPassword) {
}
