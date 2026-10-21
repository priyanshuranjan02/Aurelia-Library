package com.example.lms.service;

import com.example.lms.dto.AcceptLibrarianInvitationRequest;
import com.example.lms.dto.LibrarianInvitationRequest;
import com.example.lms.entity.Account;
import com.example.lms.entity.LibrarianInvitation;
import com.example.lms.entity.LibrarianProfile;
import com.example.lms.entity.Role;
import com.example.lms.exception.ConflictException;
import com.example.lms.exception.MailNotConfiguredException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.repository.AccountRepository;
import com.example.lms.repository.LibrarianInvitationRepository;
import com.example.lms.repository.LibrarianProfileRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LibrarianInvitationService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LibrarianInvitationRepository invitations;
    private final AccountRepository accounts;
    private final LibrarianProfileRepository librarians;
    private final PasswordEncoder passwords;
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final String smtpHost;
    private final String mailFrom;
    private final String publicBaseUrl;
    private final long expiryHours;

    public LibrarianInvitationService(
            LibrarianInvitationRepository invitations,
            AccountRepository accounts,
            LibrarianProfileRepository librarians,
            PasswordEncoder passwords,
            ObjectProvider<JavaMailSender> mailSenders,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${lms.mail.from:}") String mailFrom,
            @Value("${lms.invitation.base-url:http://localhost:8080}") String publicBaseUrl,
            @Value("${lms.invitation.expiry-hours:24}") long expiryHours) {
        this.invitations = invitations;
        this.accounts = accounts;
        this.librarians = librarians;
        this.passwords = passwords;
        this.mailSenders = mailSenders;
        this.smtpHost = smtpHost;
        this.mailFrom = mailFrom;
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.expiryHours = expiryHours;
        if (expiryHours < 1 || !StringUtils.hasText(publicBaseUrl)) {
            throw new IllegalArgumentException("Invitation expiry and public base URL must be configured correctly.");
        }
    }

    @Transactional
    public Instant invite(LibrarianInvitationRequest request, String administratorUsername) {
        ensureMailConfigured();
        String username = request.username().trim();
        String email = request.email().trim();
        if (accounts.existsByUsername(username)) {
            throw new ConflictException("That username is already in use.");
        }

        Instant now = Instant.now();
        // A new invitation for the same username invalidates older unaccepted invitations.
        for (LibrarianInvitation previous : invitations.findByUsernameAndAcceptedAtIsNull(username)) {
            previous.setExpiresAt(now);
        }

        Account inviter = accounts.findByUsername(administratorUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Administrator account was not found."));
        String rawToken = newToken();
        Instant expiresAt = now.plus(expiryHours, ChronoUnit.HOURS);

        LibrarianInvitation invitation = new LibrarianInvitation();
        invitation.setUsername(username);
        invitation.setEmail(email);
        invitation.setName(request.name().trim());
        invitation.setAge(request.age());
        invitation.setPhone(request.phone().trim());
        invitation.setTokenHash(hashToken(rawToken));
        invitation.setExpiresAt(expiresAt);
        invitation.setInvitedBy(inviter);
        invitations.save(invitation);

        sendInvitationEmail(invitation, rawToken);
        return expiresAt;
    }

    @Transactional
    public void accept(AcceptLibrarianInvitationRequest request) {
        String tokenHash = hashToken(request.token().trim());
        LibrarianInvitation invitation = invitations.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ConflictException("This invitation is invalid or has expired."));
        Instant now = Instant.now();
        if (invitation.getAcceptedAt() != null || !invitation.getExpiresAt().isAfter(now)) {
            throw new ConflictException("This invitation is invalid or has expired.");
        }
        if (accounts.existsByUsername(invitation.getUsername())) {
            throw new ConflictException("This username is already in use. Ask the administrator for a new invitation.");
        }

        Account account = new Account();
        account.setUsername(invitation.getUsername());
        account.setPasswordHash(passwords.encode(request.password()));
        account.setRole(Role.LIBRARIAN); // Role is set here on the server, never accepted from the browser.
        Account savedAccount = accounts.save(account);

        LibrarianProfile profile = new LibrarianProfile();
        profile.setAccount(savedAccount);
        profile.setName(invitation.getName());
        profile.setAge(invitation.getAge());
        profile.setPhone(invitation.getPhone());
        profile.setEmail(invitation.getEmail());
        librarians.save(profile);

        invitation.setAcceptedAt(now);
        invitations.save(invitation);
    }

    private void ensureMailConfigured() {
        if (!StringUtils.hasText(smtpHost) || !StringUtils.hasText(mailFrom) || mailSenders.getIfAvailable() == null) {
            throw new MailNotConfiguredException(
                    "Librarian invitations require SMTP settings. Configure LMS_SMTP_HOST and LMS_MAIL_FROM first.");
        }
    }

    private void sendInvitationEmail(LibrarianInvitation invitation, String rawToken) {
        JavaMailSender sender = mailSenders.getIfAvailable();
        if (sender == null) {
            throw new MailNotConfiguredException("The invitation email service is not available.");
        }
        String invitationUrl = publicBaseUrl + "/librarian-setup.html#token=" + rawToken;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom.trim());
        message.setTo(invitation.getEmail());
        message.setSubject("Your Aurelia Library staff invitation");
        message.setText("Hello " + invitation.getName() + ",\n\n"
                + "An administrator invited you to join Aurelia Library as a librarian.\n\n"
                + "Your username is: " + invitation.getUsername() + "\n\n"
                + "Choose your password using this one-time link:\n" + invitationUrl + "\n\n"
                + "The link expires at " + invitation.getExpiresAt() + " (UTC) and can be used only once.\n"
                + "If you were not expecting this invitation, you can ignore this email.\n\n"
                + "Aurelia Library");
        try {
            sender.send(message);
        } catch (MailException exception) {
            throw exception;
        }
    }

    private static String newToken() {
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    private static String stripTrailingSlash(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
