package com.example.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StudentSelfBorrowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void studentCanBorrowAndViewOnlyTheirOwnLoans() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "reader_" + suffix;
        String password = "AureliaReader!2026";

        MvcResult adminLogin = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        MvcResult bookResult = mvc.perform(post("/api/books").session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Student Borrow Test "+suffix+"\",\"author\":\"Test Author\",\"isbn\":\"978"+suffix+"\"}"))
                .andExpect(status().isCreated()).andReturn();
        long bookId = json.readTree(bookResult.getResponse().getContentAsString()).path("id").asLong();

        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\""+username+"\",\"password\":\""+password+"\",\"name\":\"Test Reader\",\"email\":\""+username+"@example.com\",\"phone\":\"555-0123\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("STUDENT"));

        MvcResult studentLogin = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\""+username+"\",\"password\":\""+password+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT")).andReturn();
        MockHttpSession studentSession = (MockHttpSession) studentLogin.getRequest().getSession(false);

        String borrowBody = "{\"bookId\":"+bookId+",\"borrowDate\":\"2026-10-20\",\"studentId\":999999}";
        MvcResult borrowResult = mvc.perform(post("/api/borrow-records/mine").session(studentSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(borrowBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BORROWED"))
                .andExpect(jsonPath("$.borrowerName").value("Test Reader"))
                .andReturn();
        JsonNode loan = json.readTree(borrowResult.getResponse().getContentAsString());
        assertThat(loan.path("studentId").asLong()).isNotEqualTo(999999L);

        mvc.perform(get("/api/borrow-records/mine").session(studentSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].bookId").value(bookId));
        mvc.perform(get("/api/borrow-records").session(studentSession))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/borrow-records/mine").session(studentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":"+bookId+",\"borrowDate\":\"2026-10-20\"}"))
                .andExpect(status().isForbidden());
    }
}
