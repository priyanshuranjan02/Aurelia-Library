package com.example.lms;

import com.example.lms.entity.Account;
import com.example.lms.entity.Role;
import com.example.lms.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableJpaAuditing
public class LibraryManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(LibraryManagementApplication.class, args);
    }

    @Bean
    CommandLineRunner seedAdmin(
            AccountRepository accounts,
            PasswordEncoder passwords,
            @Value("${lms.bootstrap-admin.username:}") String username,
            @Value("${lms.bootstrap-admin.password:}") String rawPassword) {
        return args -> {
            // Never create a predictable administrator account from source-code defaults.
            // Supply both values explicitly on first setup; existing accounts are untouched.
            if (username == null || username.isBlank()
                    || rawPassword == null || rawPassword.isBlank()) {
                return;
            }
            if (!accounts.existsByUsername(username)) {
                var admin = new Account();
                admin.setUsername(username.trim());
                admin.setPasswordHash(passwords.encode(rawPassword));
                admin.setRole(Role.ADMIN);
                accounts.save(admin);
            }
        };
    }
}
