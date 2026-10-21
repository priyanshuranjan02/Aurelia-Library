package com.example.lms.controller;

import com.example.lms.dto.AcceptLibrarianInvitationRequest;
import com.example.lms.dto.LibrarianInvitationRequest;
import com.example.lms.dto.MessageResponse;
import com.example.lms.service.LibrarianInvitationService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/librarian-invitations")
public class LibrarianInvitationController {
    private final LibrarianInvitationService invitations;

    public LibrarianInvitationController(LibrarianInvitationService invitations) {
        this.invitations = invitations;
    }

    /** Administrator-only endpoint; the token is sent by email and never returned in this response. */
    @PostMapping
    public ResponseEntity<MessageResponse> invite(@Valid @RequestBody LibrarianInvitationRequest request,
                                                   Principal principal) {
        Instant expiresAt = invitations.invite(request, principal.getName());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new MessageResponse("Invitation email sent to " + request.email().trim()
                        + ". The setup link expires at " + expiresAt + " (UTC)."));
    }

    /** Public only so an invited librarian can set a password. A valid CSRF token is still required. */
    @PostMapping("/accept")
    public ResponseEntity<MessageResponse> accept(@Valid @RequestBody AcceptLibrarianInvitationRequest request) {
        invitations.accept(request);
        return ResponseEntity.ok(new MessageResponse("Password set. You can now sign in to Aurelia Library."));
    }
}
