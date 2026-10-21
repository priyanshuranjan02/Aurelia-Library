package com.example.lms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LibrarianInvitationRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Email(message = "Invalid email address.") @Size(max = 100) String email,
        @NotBlank @Size(max = 100) String name,
        @Min(18) @Max(100) int age,
        @NotBlank @Size(max = 20) String phone) { }
