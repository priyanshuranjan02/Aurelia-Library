package com.example.lms.service;

import com.example.lms.dto.AccountDeletionRequest;
import com.example.lms.dto.PasswordChangeRequest;
import com.example.lms.dto.ProfileResponse;
import com.example.lms.dto.ProfileUpdateRequest;
import com.example.lms.entity.Account;
import com.example.lms.entity.LibrarianProfile;
import com.example.lms.entity.Role;
import com.example.lms.entity.StudentProfile;
import com.example.lms.exception.BusinessRuleException;
import com.example.lms.exception.ConflictException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.repository.AccountRepository;
import com.example.lms.repository.BorrowRecordRepository;
import com.example.lms.repository.LibrarianInvitationRepository;
import com.example.lms.repository.LibrarianProfileRepository;
import com.example.lms.repository.StudentProfileRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProfileService {
    private final AccountRepository accounts;
    private final StudentProfileRepository students;
    private final LibrarianProfileRepository librarians;
    private final BorrowRecordRepository borrowRecords;
    private final LibrarianInvitationRepository invitations;
    private final PasswordEncoder passwords;

    public ProfileService(AccountRepository accounts,
                          StudentProfileRepository students,
                          LibrarianProfileRepository librarians,
                          BorrowRecordRepository borrowRecords,
                          LibrarianInvitationRepository invitations,
                          PasswordEncoder passwords) {
        this.accounts = accounts;
        this.students = students;
        this.librarians = librarians;
        this.borrowRecords = borrowRecords;
        this.invitations = invitations;
        this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String username) {
        Account account = account(username);
        return profileFor(account);
    }

    @Transactional
    public ProfileResponse updateProfile(String currentUsername, ProfileUpdateRequest request) {
        Account account = account(currentUsername);
        String nextUsername = request.username().trim();
        if (!account.getUsername().equals(nextUsername) && accounts.existsByUsername(nextUsername)) {
            throw new ConflictException("That username is already in use.");
        }

        // Load the matching role profile before changing the account username.
        StudentProfile student = null;
        LibrarianProfile librarian = null;
        if (account.getRole() == Role.STUDENT) {
            student = students.findByAccount_Username(currentUsername).orElseThrow(
                    () -> new ResourceNotFoundException("A student profile was not found for this account."));
            requireText(request.name(), "Name is required.");
            requireText(request.email(), "Email is required.");
            requireText(request.phone(), "Phone is required.");
            student.setName(request.name().trim());
            student.setEmail(request.email().trim());
            student.setPhone(request.phone().trim());
        } else if (account.getRole() == Role.LIBRARIAN) {
            librarian = librarians.findByAccount_Username(currentUsername).orElseThrow(
                    () -> new ResourceNotFoundException("A librarian profile was not found for this account."));
            requireText(request.name(), "Name is required.");
            requireText(request.phone(), "Phone is required.");
            if (request.age() == null) {
                throw new BusinessRuleException("AGE_REQUIRED", "Age is required for a librarian profile.");
            }
            librarian.setName(request.name().trim());
            librarian.setEmail(StringUtils.hasText(request.email()) ? request.email().trim() : null);
            librarian.setPhone(request.phone().trim());
            librarian.setAge(request.age());
        }

        account.setUsername(nextUsername);
        accounts.save(account);
        return response(account, student, librarian);
    }

    @Transactional
    public void changePassword(String username, PasswordChangeRequest request) {
        Account account = account(username);
        if (!passwords.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new BusinessRuleException("INVALID_CURRENT_PASSWORD", "Your current password is incorrect.");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessRuleException("PASSWORD_CONFIRMATION_MISMATCH", "The new passwords do not match.");
        }
        if (passwords.matches(request.newPassword(), account.getPasswordHash())) {
            throw new BusinessRuleException("PASSWORD_UNCHANGED", "Choose a new password that differs from your current password.");
        }
        account.setPasswordHash(passwords.encode(request.newPassword()));
        accounts.save(account);
    }

    /** Permanently deletes login/profile data, while preserving anonymized circulation history. */
    @Transactional
    public void deleteAccount(String username, AccountDeletionRequest request) {
        Account account = account(username);
        if (!passwords.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new BusinessRuleException("INVALID_CURRENT_PASSWORD", "Your current password is incorrect.");
        }
        if (!account.getUsername().equals(request.confirmationUsername().trim())) {
            throw new BusinessRuleException("ACCOUNT_CONFIRMATION_MISMATCH", "The confirmation username does not match.");
        }
        if (account.getRole() == Role.ADMIN && accounts.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new BusinessRuleException("LAST_ACTIVE_ADMIN", "The last active administrator account cannot be deleted.");
        }
        if (account.getRole() == Role.STUDENT) {
            if (borrowRecords.existsByStudent_Account_UsernameAndReturnDateIsNull(username)) {
                throw new BusinessRuleException("ACTIVE_LOANS_REMAIN", "Return all currently borrowed books before deleting your account.");
            }
            // Detach and anonymize old records before removing the student's profile.
            borrowRecords.anonymizeHistoryForStudentUsername(username);
            students.findByAccount_Username(username).ifPresent(profile -> {
                students.delete(profile);
                students.flush();
            });
        } else if (account.getRole() == Role.LIBRARIAN) {
            // Invitations contain the librarian's personal details too; remove those copies on deletion.
            invitations.deleteInvitationsForUsername(username);
            librarians.findByAccount_Username(username).ifPresent(profile -> {
                librarians.delete(profile);
                librarians.flush();
            });
        } else if (account.getRole() == Role.ADMIN) {
            // Keep invitation records, but remove the personal-account foreign-key reference.
            invitations.clearInviterReference(account.getId());
            invitations.flush();
        }
        accounts.delete(account);
        accounts.flush();
    }

    private Account account(String username) {
        return accounts.findByUsername(username).orElseThrow(
                () -> new ResourceNotFoundException("The signed-in account was not found."));
    }

    private ProfileResponse profileFor(Account account) {
        return switch (account.getRole()) {
            case STUDENT -> students.findByAccount_Username(account.getUsername())
                    .map(profile -> response(account, profile, null))
                    .orElseThrow(() -> new ResourceNotFoundException("A student profile was not found for this account."));
            case LIBRARIAN -> librarians.findByAccount_Username(account.getUsername())
                    .map(profile -> response(account, null, profile))
                    .orElseThrow(() -> new ResourceNotFoundException("A librarian profile was not found for this account."));
            case ADMIN -> response(account, null, null);
        };
    }

    private ProfileResponse response(Account account, StudentProfile student, LibrarianProfile librarian) {
        if (student != null) {
            return new ProfileResponse(account.getId(), account.getUsername(), account.getRole().name(),
                    student.getName(), student.getEmail(), student.getPhone(), null);
        }
        if (librarian != null) {
            return new ProfileResponse(account.getId(), account.getUsername(), account.getRole().name(),
                    librarian.getName(), librarian.getEmail(), librarian.getPhone(), librarian.getAge());
        }
        // Administrators have no separate profile table, so their username is also their display name.
        return new ProfileResponse(account.getId(), account.getUsername(), account.getRole().name(),
                account.getUsername(), null, null, null);
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessRuleException("PROFILE_FIELD_REQUIRED", message);
        }
    }
}
