package com.example.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lms.entity.Role;
import com.example.lms.repository.AccountRepository;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.mail.host=smtp.test.invalid",
        "lms.mail.from=noreply@example.test",
        "lms.invitation.base-url=http://localhost:8080",
        "lms.invitation.expiry-hours=24"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LibrarianInvitationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @MockitoBean JavaMailSender mailSender;

    @Test
    void invitedLibrarianSetsPasswordAndTokenCanOnlyBeUsedOnce() throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession adminSession = (MockHttpSession) login.getRequest().getSession(false);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "librarian_" + suffix;
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + username
                + "@example.test\",\"name\":\"Invited Librarian\",\"age\":31,\"phone\":\"555-0181\"}";

        MvcResult invited = mvc.perform(post("/api/librarian-invitations").session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invitation email sent")))
                .andReturn();

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        String emailText = mail.getValue().getText();
        Matcher tokenMatcher = Pattern.compile("librarian-setup\\.html#token=([A-Za-z0-9_-]+)").matcher(emailText);
        assertThat(tokenMatcher.find()).isTrue();
        String token = tokenMatcher.group(1);
        assertThat(invited.getResponse().getContentAsString()).doesNotContain(token);

        String newPassword = "AureliaLibrarian!2026";
        String acceptBody = "{\"token\":\"" + token + "\",\"password\":\"" + newPassword + "\"}";
        mvc.perform(post("/api/librarian-invitations/accept").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(acceptBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("You can now sign in")));

        var account = accounts.findByUsername(username).orElseThrow();
        assertThat(account.getRole()).isEqualTo(Role.LIBRARIAN);
        assertThat(account.getPasswordHash()).isNotEqualTo(newPassword);
        assertThat(passwords.matches(newPassword, account.getPasswordHash())).isTrue();

        mvc.perform(post("/api/librarian-invitations/accept").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(acceptBody))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("LIBRARIAN"));
    }

    @Test
    void acceptingInvitationStillRequiresCsrf() throws Exception {
        mvc.perform(post("/api/librarian-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"not-a-valid-token\",\"password\":\"AureliaLibrarian!2026\"}"))
                .andExpect(status().isForbidden());
    }
}
