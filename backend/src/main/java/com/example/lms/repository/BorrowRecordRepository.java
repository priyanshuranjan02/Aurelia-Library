package com.example.lms.repository;

import com.example.lms.entity.BorrowRecord;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {
    List<BorrowRecord> findByReturnDateIsNull();

    List<BorrowRecord> findByReturnDateIsNotNull();

    boolean existsByBookId(Long bookId);

    boolean existsByStudentId(Long studentId);

    /** Prevents two simultaneous return requests from both succeeding. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BorrowRecord r where r.id = :id")
    Optional<BorrowRecord> findByIdForUpdate(@Param("id") Long id);
}
