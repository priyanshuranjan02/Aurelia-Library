package com.example.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "lms.mail.provider=brevo",
        "lms.mail.api-key=test-api-key-never-used-outside-local-test",
        "lms.mail.from=noreply@example.test",
        "lms.mail.from-name=Aurelia Test",
        "lms.invitation.base-url=https://aurelia.example.test",
        "lms.invitation.expiry-hours=24"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BrevoEmailApiIntegrationTest {
    private static final AtomicReference<String> receivedApiKey = new AtomicReference<>();
    private static final AtomicReference<String> receivedBody = new AtomicReference<>();
    private static HttpServer server;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerLocalEmailApi(DynamicPropertyRegistry registry) {
        registry.add("lms.mail.api-url", BrevoEmailApiIntegrationTest::startServerAndGetUrl);
    }

    private static synchronized String startServerAndGetUrl() {
        if (server == null) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/v3/smtp/email", exchange -> {
                    receivedApiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
                    receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    byte[] response = "{\"messageId\":\"local-test-message-id\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(201, response.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(response);
                    }
                });
                server.start();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not start local Brevo API test server.", exception);
            }
        }
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email";
    }

    @AfterAll
    static void stopLocalEmailApi() {
        if (server != null) server.stop(0);
    }

    @Test
    void invitationUsesEmailApiPayloadContractWithoutExposingTokenInApiResponse() throws Exception {
        receivedApiKey.set(null);
        receivedBody.set(null);

        MvcResult login = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession adminSession = (MockHttpSession) login.getRequest().getSession(false);

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "brevo_" + suffix;
        String email = username + "@example.test";
        String requestBody = "{\"username\":\"" + username + "\",\"email\":\"" + email
                + "\",\"name\":\"API Test Librarian\",\"age\":29,\"phone\":\"555-0102\"}";

        var response = mvc.perform(post("/api/librarian-invitations").session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isAccepted())
                .andReturn();

        assertThat(receivedApiKey.get()).isEqualTo("test-api-key-never-used-outside-local-test");
        assertThat(receivedBody.get()).isNotBlank();
        JsonNode json = objectMapper.readTree(receivedBody.get());
        assertThat(json.path("sender").path("name").asText()).isEqualTo("Aurelia Test");
        assertThat(json.path("sender").path("email").asText()).isEqualTo("noreply@example.test");
        assertThat(json.path("to").get(0).path("email").asText()).isEqualTo(email);
        assertThat(json.path("subject").asText()).contains("invitation");
        assertThat(json.path("textContent").asText()).contains("librarian-setup.html#token=");
        assertThat(response.getResponse().getContentAsString()).doesNotContain("#token=");
    }
}
