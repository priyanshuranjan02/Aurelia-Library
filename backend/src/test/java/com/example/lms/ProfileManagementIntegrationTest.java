package com.example.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lms.dto.AccountDeletionRequest;
import com.example.lms.entity.Account;
import com.example.lms.entity.Book;
import com.example.lms.entity.BorrowRecord;
import com.example.lms.entity.Role;
import com.example.lms.entity.StudentProfile;
import com.example.lms.exception.BusinessRuleException;
import com.example.lms.repository.AccountRepository;
import com.example.lms.repository.BookRepository;
import com.example.lms.repository.BorrowRecordRepository;
import com.example.lms.repository.StudentProfileRepository;
import com.example.lms.service.ProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProfileManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired StudentProfileRepository students;
    @Autowired BookRepository books;
    @Autowired BorrowRecordRepository records;
    @Autowired ProfileService profiles;
    @Autowired PasswordEncoder passwords;

    @Test
    void studentCanEditProfileAndChangeUsernameWithoutLosingSession() throws Exception {
        String suffix = suffix();
        String username = "profile_" + suffix;
        String newUsername = "updated_" + suffix;
        String password = "AureliaReader!2026";
        registerStudent(username, password);
        MvcResult login = login(username, password);
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(patch("/api/profile").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + newUsername + "\",\"name\":\"Updated Reader\","
                                + "\"email\":\"updated-" + suffix + "@example.com\",\"phone\":\"555-0199\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(newUsername))
                .andExpect(jsonPath("$.displayName").value("Updated Reader"));

        mvc.perform(get("/api/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(newUsername))
                .andExpect(jsonPath("$.email").value("updated-" + suffix + "@example.com"));
    }

    @Test
    void passwordChangeVerifiesCurrentPasswordAndStoresAHash() throws Exception {
        String username = "password_" + suffix();
        String originalPassword = "AureliaReader!2026";
        String newPassword = "AureliaFresh!2027";
        registerStudent(username, originalPassword);
        MvcResult login = login(username, originalPassword);
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(post("/api/profile/change-password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-current\",\"newPassword\":\"" + newPassword
                                + "\",\"confirmPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/profile/change-password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + originalPassword + "\",\"newPassword\":\"" + newPassword
                                + "\",\"confirmPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isNoContent());

        var saved = accounts.findByUsername(username).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(newPassword);
        assertThat(passwords.matches(newPassword, saved.getPasswordHash())).isTrue();
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + originalPassword + "\"}"))
                .andExpect(status().isUnauthorized());
        login(username, newPassword);
    }

    @Test
    void deletingStudentAnonymizesAndPreservesReturnedLoanHistory() {
        String username = "delete_" + suffix();
        String password = "AureliaReader!2026";
        Account account = new Account();
        account.setUsername(username);
        account.setPasswordHash(passwords.encode(password));
        account.setRole(Role.STUDENT);
        account.setEnabled(true);
        account = accounts.saveAndFlush(account);

        StudentProfile profile = new StudentProfile();
        profile.setAccount(account);
        profile.setName("Privacy Test Student");
        profile.setEmail("privacy-" + suffix() + "@example.com");
        profile.setPhone("555-0188");
        profile = students.saveAndFlush(profile);

        Book book = new Book();
        book.setTitle("Privacy Test Book " + suffix());
        book.setAuthor("Test Author");
        book.setIsbn(null);
        book.setPublishedDate(null);
        book.setAvailable(true);
        book = books.saveAndFlush(book);

        BorrowRecord record = new BorrowRecord();
        record.setBook(book);
        record.setStudent(profile);
        record.setBorrowerName(profile.getName());
        record.setBorrowerEmail(profile.getEmail());
        record.setBorrowerPhone(profile.getPhone());
        record.setBorrowDate(LocalDate.now().minusDays(7));
        record.setReturnDate(LocalDate.now().minusDays(1));
        record = records.saveAndFlush(record);
        Long recordId = record.getId();
        Long profileId = profile.getId();

        profiles.deleteAccount(username, new AccountDeletionRequest(password, username));

        assertThat(accounts.findByUsername(username)).isEmpty();
        assertThat(students.findById(profileId)).isEmpty();
        BorrowRecord retained = records.findById(recordId).orElseThrow();
        assertThat(retained.getStudent()).isNull();
        assertThat(retained.getBorrowerName()).isEqualTo("Former member");
        assertThat(retained.getBorrowerEmail()).isEqualTo("removed@privacy.invalid");
        assertThat(retained.getBorrowerPhone()).isEqualTo("REDACTED");
        assertThat(retained.getBorrowDate()).isEqualTo(LocalDate.now().minusDays(7));
    }

    @Test
    void theLastActiveAdministratorCannotDeleteTheirAccount() {
        Account administrator = accounts.findByUsername("admin").orElseThrow();
        assertThat(accounts.countByRoleAndEnabledTrue(Role.ADMIN)).isEqualTo(1);

        assertThatThrownBy(() -> profiles.deleteAccount("admin",
                new AccountDeletionRequest("ChangeMe123!", "admin")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("last active administrator");
        assertThat(accounts.findByUsername(administrator.getUsername())).isPresent();
    }

    @Test
    void studentCannotDeleteAccountWithAnOutstandingLoan() {
        String username = "active_" + suffix();
        String password = "AureliaReader!2026";
        Account account = new Account();
        account.setUsername(username);
        account.setPasswordHash(passwords.encode(password));
        account.setRole(Role.STUDENT);
        account.setEnabled(true);
        account = accounts.saveAndFlush(account);
        StudentProfile student = new StudentProfile();
        student.setAccount(account);
        student.setName("Active Loan Student");
        student.setEmail(username + "@example.com");
        student.setPhone("555-0181");
        student = students.saveAndFlush(student);
        Book book = new Book();
        book.setTitle("Active Loan Book " + suffix());
        book.setAuthor("Test Author");
        book.setAvailable(false);
        book = books.saveAndFlush(book);
        BorrowRecord record = new BorrowRecord();
        record.setBook(book);
        record.setStudent(student);
        record.setBorrowerName(student.getName());
        record.setBorrowerEmail(student.getEmail());
        record.setBorrowerPhone(student.getPhone());
        record.setBorrowDate(LocalDate.now().minusDays(1));
        records.saveAndFlush(record);

        assertThatThrownBy(() -> profiles.deleteAccount(username, new AccountDeletionRequest(password, username)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Return all currently borrowed books");
        assertThat(accounts.findByUsername(username)).isPresent();
    }

    private void registerStudent(String username, String password) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password
                                + "\",\"name\":\"Profile Reader\",\"email\":\"" + username
                                + "@example.com\",\"phone\":\"555-0102\"}"))
                .andExpect(status().isCreated());
    }

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
