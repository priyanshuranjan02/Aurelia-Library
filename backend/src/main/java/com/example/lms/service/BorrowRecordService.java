package com.example.lms.service;

import com.example.lms.dto.BorrowRecordResponse;
import com.example.lms.dto.BorrowRequest;
import com.example.lms.dto.StudentBorrowRequest;
import com.example.lms.entity.Book;
import com.example.lms.entity.BorrowRecord;
import com.example.lms.entity.StudentProfile;
import com.example.lms.exception.BusinessRuleException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.repository.BookRepository;
import com.example.lms.repository.BorrowRecordRepository;
import com.example.lms.repository.StudentProfileRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BorrowRecordService {
    private final BorrowRecordRepository records;
    private final BookRepository books;
    private final StudentProfileRepository students;

    public BorrowRecordService(BorrowRecordRepository records,
                               BookRepository books,
                               StudentProfileRepository students) {
        this.records = records;
        this.books = books;
        this.students = students;
    }

    @Transactional(readOnly = true)
    public List<BorrowRecordResponse> list(String status) {
        var source = "BORROWED".equalsIgnoreCase(status)
                ? records.findByReturnDateIsNull()
                : "RETURNED".equalsIgnoreCase(status)
                    ? records.findByReturnDateIsNotNull()
                    : records.findAll();
        return source.stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<BorrowRecordResponse> listForStudent(String username, String status) {
        var source = records.findAllForStudentUsername(username).stream();
        if ("BORROWED".equalsIgnoreCase(status)) {
            source = source.filter(record -> record.getReturnDate() == null);
        } else if ("RETURNED".equalsIgnoreCase(status)) {
            source = source.filter(record -> record.getReturnDate() != null);
        }
        return source.map(this::response).toList();
    }

    @Transactional
    public BorrowRecordResponse borrow(BorrowRequest request) {
        StudentProfile student = students.findById(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student %d was not found.".formatted(request.studentId())));
        return createBorrow(student, request.bookId(), request.borrowDate());
    }

    /** Student borrow operation derives the student profile from the authenticated session. */
    @Transactional
    public BorrowRecordResponse borrowForStudent(String username, StudentBorrowRequest request) {
        StudentProfile student = students.findByAccount_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("A student profile was not found for this account."));
        return createBorrow(student, request.bookId(), request.borrowDate());
    }

    private BorrowRecordResponse createBorrow(StudentProfile student, Long bookId, LocalDate borrowDate) {
        // Lock the book before checking availability to prevent concurrent double-borrows.
        Book book = books.findByIdForUpdate(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book %d was not found.".formatted(bookId)));
        if (!book.isAvailable()) {
            throw new BusinessRuleException("BOOK_UNAVAILABLE", "The selected book is not available for borrowing.");
        }
        var record = new BorrowRecord();
        record.setBook(book);
        record.setStudent(student);
        record.setBorrowerName(student.getName());
        record.setBorrowerEmail(student.getEmail());
        record.setBorrowerPhone(student.getPhone());
        record.setBorrowDate(borrowDate);
        book.setAvailable(false);
        return response(records.save(record));
    }

    @Transactional
    public void returnBook(Long id) {
        var record = records.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Borrow record %d was not found.".formatted(id)));
        if (record.getReturnDate() != null) {
            throw new BusinessRuleException("ALREADY_RETURNED", "This borrow record has already been returned.");
        }
        record.setReturnDate(LocalDate.now());
        record.getBook().setAvailable(true);
    }

    private BorrowRecordResponse response(BorrowRecord record) {
        return new BorrowRecordResponse(
                record.getId(),
                record.getBook().getId(),
                record.getBook().getTitle(),
                record.getStudent() == null ? null : record.getStudent().getId(),
                record.getBorrowerName(),
                record.getBorrowerEmail(),
                record.getBorrowerPhone(),
                record.getBorrowDate(),
                record.getReturnDate(),
                record.getReturnDate() == null ? "BORROWED" : "RETURNED");
    }
}
