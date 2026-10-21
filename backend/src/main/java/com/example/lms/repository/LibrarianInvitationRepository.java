package com.example.lms.repository;

import com.example.lms.entity.LibrarianInvitation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibrarianInvitationRepository extends JpaRepository<LibrarianInvitation, Long> {
    List<LibrarianInvitation> findByUsernameAndAcceptedAtIsNull(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from LibrarianInvitation invitation where invitation.tokenHash = :tokenHash")
    Optional<LibrarianInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** Preserve invitations for other librarians while removing references to a deleted administrator. */
    @Modifying(flushAutomatically = true)
    @Query("update LibrarianInvitation invitation set invitation.invitedBy = null where invitation.invitedBy.id = :accountId")
    int clearInviterReference(@Param("accountId") Long accountId);

    /** Remove the invitation's personal data when the invited librarian deletes their account. */
    @Modifying(flushAutomatically = true)
    @Query("delete from LibrarianInvitation invitation where invitation.username = :username")
    int deleteInvitationsForUsername(@Param("username") String username);
}
