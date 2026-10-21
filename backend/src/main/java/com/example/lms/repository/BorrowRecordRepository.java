package com.example.lms.repository;

import com.example.lms.entity.BorrowRecord;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {
    List<BorrowRecord> findByReturnDateIsNull();

    List<BorrowRecord> findByReturnDateIsNotNull();

    boolean existsByBookId(Long bookId);

    boolean existsByStudentId(Long studentId);

    boolean existsByStudent_Account_UsernameAndReturnDateIsNull(String username);

    /** Lists only the loans owned by the authenticated student's account. */
    @Query("select r from BorrowRecord r join r.student s join s.account a where a.username = :username order by r.id desc")
    List<BorrowRecord> findAllForStudentUsername(@Param("username") String username);

    /** Preserve transaction dates/books but remove personal identifiers when a student deletes their account. */
    @Modifying(flushAutomatically = true)
    @Query("update BorrowRecord r set r.student = null, r.borrowerName = 'Former member', "
            + "r.borrowerEmail = 'removed@privacy.invalid', r.borrowerPhone = 'REDACTED' "
            + "where r.student.id in (select s.id from StudentProfile s where s.account.username = :username)")
    int anonymizeHistoryForStudentUsername(@Param("username") String username);

    /** Prevents two simultaneous return requests from both succeeding. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BorrowRecord r where r.id = :id")
    Optional<BorrowRecord> findByIdForUpdate(@Param("id") Long id);
}
