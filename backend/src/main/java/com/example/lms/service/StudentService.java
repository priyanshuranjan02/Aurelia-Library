package com.example.lms.service;

import com.example.lms.dto.StudentRequest;
import com.example.lms.dto.StudentResponse;
import com.example.lms.dto.StudentUpdateRequest;
import com.example.lms.entity.Account;
import com.example.lms.entity.Role;
import com.example.lms.entity.StudentProfile;
import com.example.lms.exception.ConflictException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.repository.AccountRepository;
import com.example.lms.repository.StudentProfileRepository;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {
    private final StudentProfileRepository students;
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;

    public StudentService(StudentProfileRepository students, AccountRepository accounts, PasswordEncoder passwords) {
        this.students = students;
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> list() {
        return students.findAll().stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public StudentResponse get(Long id) {
        return response(student(id));
    }

    @Transactional
    public StudentResponse create(StudentRequest request) {
        String username = request.username().trim();
        if (accounts.existsByUsername(username)) {
            throw new ConflictException("Username is already in use.");
        }

        var account = new Account();
        account.setUsername(username);
        account.setPasswordHash(passwords.encode(request.password()));
        // The role is selected by the server, never from a public request field.
        account.setRole(Role.STUDENT);

        var profile = new StudentProfile();
        profile.setAccount(accounts.save(account));
        apply(profile, request);
        return response(students.save(profile));
    }

    @Transactional
    public StudentResponse update(Long id, StudentUpdateRequest request) {
        var profile = student(id);
        String username = request.username().trim();
        if (!profile.getAccount().getUsername().equals(username) && accounts.existsByUsername(username)) {
            throw new ConflictException("Username is already in use.");
        }
        profile.getAccount().setUsername(username);
        apply(profile, request);
        return response(students.save(profile));
    }

    @Transactional
    public void delete(Long id) {
        students.delete(student(id));
    }

    private StudentProfile student(Long id) {
        return students.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Student %d was not found.".formatted(id)));
    }

    private void apply(StudentProfile profile, StudentRequest request) {
        profile.setName(request.name().trim());
        profile.setEmail(request.email().trim());
        profile.setPhone(request.phone().trim());
    }

    private void apply(StudentProfile profile, StudentUpdateRequest request) {
        profile.setName(request.name().trim());
        profile.setEmail(request.email().trim());
        profile.setPhone(request.phone().trim());
    }

    private StudentResponse response(StudentProfile profile) {
        var account = profile.getAccount();
        return new StudentResponse(profile.getId(), account.getId(), account.getUsername(), profile.getName(),
                profile.getEmail(), profile.getPhone(), account.getRole().name());
    }
}
