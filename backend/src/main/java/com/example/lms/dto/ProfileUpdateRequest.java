package com.example.lms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Only profile fields are accepted; role and enabled status can never be changed here. */
public record ProfileUpdateRequest(
        @NotBlank(message = "Username is required.")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters.")
        String username,
        @Size(max = 100, message = "Name must be 100 characters or fewer.")
        String name,
        @Email(message = "Enter a valid email address.")
        @Size(max = 100, message = "Email must be 100 characters or fewer.")
        String email,
        @Size(max = 20, message = "Phone must be 20 characters or fewer.")
        String phone,
        @Min(value = 18, message = "Librarians must be at least 18 years old.")
        @Max(value = 120, message = "Enter a valid age.")
        Integer age) {
}
