package com.example.lms;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lms.entity.Role;
import com.example.lms.repository.AccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PublicStudentRegistrationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;

    @Test
    void publicRegistrationCreatesStudentWithHashedPasswordAndNoElevatedRole() throws Exception {
        String username = "reader_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String password = "AureliaReader!2026";
        String body = "{\"username\":\" " + username + " \",\"password\":\"" + password
                + "\",\"name\":\"New Reader\",\"email\":\"" + username
                + "@example.com\",\"phone\":\"555-0119\",\"role\":\"ADMIN\"}";

        MvcResult result = mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andReturn();

        JsonNode response = json.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(response.has("password")).isFalse();
        var account = accounts.findByUsername(username).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(account.getRole()).isEqualTo(Role.STUDENT);
        org.assertj.core.api.Assertions.assertThat(account.getPasswordHash()).isNotEqualTo(password);
        org.assertj.core.api.Assertions.assertThat(passwords.matches(password, account.getPasswordHash())).isTrue();
    }

    @Test
    void registrationRejectsDuplicateUsername() throws Exception {
        String username = "reader_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String first = registrationBody(username, username + "1@example.com");
        String duplicate = registrationBody(username, username + "2@example.com");

        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(duplicate))
                .andExpect(status().isConflict());
    }

    @Test
    void publicRegistrationStillRequiresCsrfToken() throws Exception {
        String body = registrationBody("reader_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                "new-reader@example.com");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    private String registrationBody(String username, String email) {
        return "{\"username\":\"" + username + "\",\"password\":\"AureliaReader!2026\","
                + "\"name\":\"New Reader\",\"email\":\"" + email + "\",\"phone\":\"555-0119\"}";
    }
}
