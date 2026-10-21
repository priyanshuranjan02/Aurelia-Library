package com.example.lms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptLibrarianInvitationRequest(
        @NotBlank @Size(max = 100) String token,
        @NotBlank @Size(min = 12, max = 100) String password) { }
