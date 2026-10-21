package com.example.lms.service;

import com.example.lms.dto.LibrarianRequest;
import com.example.lms.dto.LibrarianResponse;
import com.example.lms.dto.LibrarianUpdateRequest;
import com.example.lms.entity.Account;
import com.example.lms.entity.LibrarianProfile;
import com.example.lms.entity.Role;
import com.example.lms.exception.ConflictException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.repository.AccountRepository;
import com.example.lms.repository.LibrarianProfileRepository;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibrarianService {
    private final LibrarianProfileRepository librarians;
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;

    public LibrarianService(LibrarianProfileRepository librarians, AccountRepository accounts, PasswordEncoder passwords) {
        this.librarians = librarians;
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public List<LibrarianResponse> list() {
        return librarians.findAll().stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public LibrarianResponse get(Long id) {
        return response(librarian(id));
    }

    /** Retained for existing administrator-operated API clients; the UI uses email invitations for new staff. */
    @Transactional
    public LibrarianResponse create(LibrarianRequest request) {
        String username = request.username().trim();
        if (accounts.existsByUsername(username)) {
            throw new ConflictException("Username is already in use.");
        }
        Account account = new Account();
        account.setUsername(username);
        account.setPasswordHash(passwords.encode(request.password()));
        account.setRole(Role.LIBRARIAN);
        LibrarianProfile profile = new LibrarianProfile();
        profile.setAccount(accounts.save(account));
        apply(profile, request);
        return response(librarians.save(profile));
    }

    @Transactional
    public LibrarianResponse update(Long id, LibrarianUpdateRequest request) {
        LibrarianProfile profile = librarian(id);
        String username = request.username().trim();
        if (!profile.getAccount().getUsername().equals(username) && accounts.existsByUsername(username)) {
            throw new ConflictException("Username is already in use.");
        }
        profile.getAccount().setUsername(username);
        apply(profile, request);
        return response(librarians.save(profile));
    }

    @Transactional
    public void delete(Long id) {
        librarians.delete(librarian(id));
    }

    private LibrarianProfile librarian(Long id) {
        return librarians.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Librarian %d was not found.".formatted(id)));
    }

    private void apply(LibrarianProfile profile, LibrarianRequest request) {
        profile.setName(request.name().trim());
        profile.setAge(request.age());
        profile.setPhone(request.phone().trim());
    }

    private void apply(LibrarianProfile profile, LibrarianUpdateRequest request) {
        profile.setName(request.name().trim());
        profile.setAge(request.age());
        profile.setPhone(request.phone().trim());
    }

    private LibrarianResponse response(LibrarianProfile profile) {
        Account account = profile.getAccount();
        return new LibrarianResponse(profile.getId(), account.getId(), account.getUsername(), profile.getName(),
                profile.getAge(), profile.getPhone(), profile.getEmail(), account.getRole().name());
    }
}
