package com.example.lms.controller;

import com.example.lms.dto.AccountDeletionRequest;
import com.example.lms.dto.PasswordChangeRequest;
import com.example.lms.dto.ProfileResponse;
import com.example.lms.dto.ProfileUpdateRequest;
import com.example.lms.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ProfileResponse get(Authentication authentication) {
        return profiles.getProfile(authentication.getName());
    }

    @PatchMapping
    public ProfileResponse update(@Valid @RequestBody ProfileUpdateRequest request,
                                  Authentication authentication,
                                  HttpServletRequest servletRequest) {
        String oldUsername = authentication.getName();
        ProfileResponse updated = profiles.updateProfile(oldUsername, request);
        if (!oldUsername.equals(updated.username())) {
            // A username change must also update the session principal or the next request
            // would continue looking up the account under its previous username.
            var replacement = new UsernamePasswordAuthenticationToken(
                    updated.username(), null, authentication.getAuthorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(replacement);
            SecurityContextHolder.setContext(context);
            servletRequest.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        }
        return updated;
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordChangeRequest request, Authentication authentication) {
        profiles.changePassword(authentication.getName(), request);
    }

    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@Valid @RequestBody AccountDeletionRequest request,
                              Authentication authentication,
                              HttpServletRequest servletRequest) {
        profiles.deleteAccount(authentication.getName(), request);
        var session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
