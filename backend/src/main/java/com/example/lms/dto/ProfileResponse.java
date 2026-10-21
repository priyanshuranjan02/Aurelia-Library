package com.example.lms.dto;

/** Safe, role-aware view of the signed-in account and its editable profile fields. */
public record ProfileResponse(
        Long accountId,
        String username,
        String role,
        String displayName,
        String email,
        String phone,
        Integer age) {
}
