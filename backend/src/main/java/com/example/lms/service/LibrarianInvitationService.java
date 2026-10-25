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
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
    private final String mailFromName;
    private final String emailProvider;
    private final String emailApiKey;
    private final String emailApiUrl;
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
            @Value("${lms.invitation.expiry-hours:24}") long expiryHours,
            @Value("${lms.mail.provider:smtp}") String emailProvider,
            @Value("${lms.mail.api-key:}") String emailApiKey,
            @Value("${lms.mail.from-name:Aurelia Library}") String mailFromName,
            @Value("${lms.mail.api-url:https://api.brevo.com/v3/smtp/email}") String emailApiUrl) {
        this.invitations = invitations;
        this.accounts = accounts;
        this.librarians = librarians;
        this.passwords = passwords;
        this.mailSenders = mailSenders;
        this.smtpHost = smtpHost;
        this.mailFrom = mailFrom;
        this.mailFromName = mailFromName;
        this.emailProvider = emailProvider == null ? "smtp" : emailProvider.trim().toLowerCase(Locale.ROOT);
        this.emailApiKey = emailApiKey;
        this.emailApiUrl = emailApiUrl;
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
        if ("brevo".equals(emailProvider)) {
            if (!StringUtils.hasText(emailApiKey) || !StringUtils.hasText(senderEmail()) || !StringUtils.hasText(emailApiUrl)) {
                throw new MailNotConfiguredException(
                        "Librarian invitations require LMS_EMAIL_API_KEY and a verified sender address in LMS_MAIL_FROM when using Brevo.");
            }
            return;
        }
        if (!"smtp".equals(emailProvider)) {
            throw new MailNotConfiguredException("Unsupported LMS_EMAIL_PROVIDER. Configure 'smtp' or 'brevo'.");
        }
        if (!StringUtils.hasText(smtpHost) || !StringUtils.hasText(mailFrom) || mailSenders.getIfAvailable() == null) {
            throw new MailNotConfiguredException(
                    "Librarian invitations require SMTP settings. Configure LMS_SMTP_HOST and LMS_MAIL_FROM first.");
        }
    }

    private void sendInvitationEmail(LibrarianInvitation invitation, String rawToken) {
        String invitationUrl = publicBaseUrl + "/librarian-setup.html#token=" + rawToken;
        String subject = "Your Aurelia Library staff invitation";
        String text = "Hello " + invitation.getName() + ",\n\n"
                + "An administrator invited you to join Aurelia Library as a librarian.\n\n"
                + "Your username is: " + invitation.getUsername() + "\n\n"
                + "Choose your password using this one-time link:\n" + invitationUrl + "\n\n"
                + "The link expires at " + invitation.getExpiresAt() + " (UTC) and can be used only once.\n"
                + "If you were not expecting this invitation, you can ignore this email.\n\n"
                + "Aurelia Library";

        if ("brevo".equals(emailProvider)) {
            sendWithBrevo(invitation, subject, text);
            return;
        }

        JavaMailSender sender = mailSenders.getIfAvailable();
        if (sender == null) {
            throw new MailNotConfiguredException("The configured SMTP email service is not available.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom.trim());
        message.setTo(invitation.getEmail());
        message.setSubject(subject);
        message.setText(text);
        sender.send(message);
    }

    private void sendWithBrevo(LibrarianInvitation invitation, String subject, String text) {
        Map<String, Object> sender = Map.of("name", senderName(), "email", senderEmail());
        Map<String, Object> recipient = Map.of("email", invitation.getEmail(), "name", invitation.getName());
        Map<String, Object> payload = Map.of(
                "sender", sender,
                "to", List.of(recipient),
                "subject", subject,
                "textContent", text);
        try {
            RestClient.create()
                    .post()
                    .uri(emailApiUrl.trim())
                    .header("api-key", emailApiKey.trim())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new MailSendException("The invitation email could not be sent through the configured email API.", exception);
        }
    }

    private String senderEmail() {
        if (!StringUtils.hasText(mailFrom)) return null;
        try {
            InternetAddress[] addresses = InternetAddress.parse(mailFrom.trim(), true);
            return addresses.length == 1 ? addresses[0].getAddress() : null;
        } catch (AddressException exception) {
            return null;
        }
    }

    private String senderName() {
        if (StringUtils.hasText(mailFromName)) return mailFromName.trim();
        if (StringUtils.hasText(mailFrom)) {
            try {
                InternetAddress[] addresses = InternetAddress.parse(mailFrom.trim(), true);
                if (addresses.length == 1 && StringUtils.hasText(addresses[0].getPersonal())) {
                    return addresses[0].getPersonal();
                }
            } catch (AddressException ignored) {
                // Use the neutral default if the optional display name cannot be parsed.
            }
        }
        return "Aurelia Library";
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
