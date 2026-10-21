package com.example.lms.repository;

import com.example.lms.entity.LibrarianProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibrarianProfileRepository extends JpaRepository<LibrarianProfile, Long> {
    Optional<LibrarianProfile> findByAccount_Username(String username);
}
