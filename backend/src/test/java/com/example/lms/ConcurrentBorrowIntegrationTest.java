package com.example.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConcurrentBorrowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void onlyOneOfTwoConcurrentRequestsCanBorrowTheSameBook() throws Exception {
        String token = UUID.randomUUID().toString().replace("-", "");
        MockHttpSession setupSession = loginAsAdmin();

        String studentBody = "{\"username\":\"borrower" + token.substring(0, 12)
                + "\",\"password\":\"Password123!\",\"name\":\"Concurrent Borrower\",\"email\":\"borrower"
                + token.substring(0, 12) + "@example.com\",\"phone\":\"555-0177\"}";
        MvcResult studentResult = mvc.perform(post("/api/students")
                        .session(setupSession).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(studentBody))
                .andExpect(status().isCreated()).andReturn();
        long studentId = json.readTree(studentResult.getResponse().getContentAsString()).path("id").asLong();

        String isbn = "978" + Long.toString(System.currentTimeMillis());
        String bookBody = "{\"title\":\"Concurrency Test Book\",\"author\":\"Test Suite\",\"isbn\":\""
                + isbn + "\",\"publishedDate\":\"2020-01-01\"}";
        MvcResult bookResult = mvc.perform(post("/api/books")
                        .session(setupSession).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(bookBody))
                .andExpect(status().isCreated()).andReturn();
        long bookId = json.readTree(bookResult.getResponse().getContentAsString()).path("id").asLong();

        // Each request uses its own authenticated session and its own thread/transaction.
        MockHttpSession firstSession = loginAsAdmin();
        MockHttpSession secondSession = loginAsAdmin();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        String borrowBody = "{\"bookId\":" + bookId + ",\"studentId\":" + studentId
                + ",\"borrowerName\":\"Ignored by server\",\"borrowerEmail\":\"ignored@example.com\",\"borrowerPhone\":\"0\",\"borrowDate\":\""
                + LocalDate.now() + "\"}";

        Callable<MvcResult> firstRequest = borrowTask(firstSession, borrowBody, ready, start);
        Callable<MvcResult> secondRequest = borrowTask(secondSession, borrowBody, ready, start);
        try {
            Future<MvcResult> first = pool.submit(firstRequest);
            Future<MvcResult> second = pool.submit(secondRequest);
            assertThat(ready.await(5, TimeUnit.SECONDS)).as("both requests should reach the start barrier").isTrue();
            start.countDown();

            MvcResult resultA = first.get(15, TimeUnit.SECONDS);
            MvcResult resultB = second.get(15, TimeUnit.SECONDS);
            List<Integer> statuses = List.of(resultA.getResponse().getStatus(), resultB.getResponse().getStatus());
            assertThat(statuses).containsExactlyInAnyOrder(201, 400);

            MvcResult rejected = resultA.getResponse().getStatus() == 400 ? resultA : resultB;
            JsonNode error = json.readTree(rejected.getResponse().getContentAsString());
            assertThat(error.path("code").asText()).isEqualTo("BOOK_UNAVAILABLE");
        } finally {
            start.countDown();
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Callable<MvcResult> borrowTask(
            MockHttpSession session, String body, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test start barrier timed out.");
            }
            return mvc.perform(post("/api/borrow-records")
                            .session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn();
        };
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}