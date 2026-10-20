package com.example.lms.service;

import com.example.lms.dto.BorrowRecordResponse;
import com.example.lms.dto.BorrowRequest;
import com.example.lms.entity.Book;
import com.example.lms.entity.BorrowRecord;
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

    @Transactional
    public BorrowRecordResponse borrow(BorrowRequest request) {
        // Lock first, then inspect availability. A regular findById() here allows
        // two transactions to both observe available=true before either commits.
        Book book = books.findByIdForUpdate(request.bookId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Book %d was not found.".formatted(request.bookId())));

        if (!book.isAvailable()) {
            throw new BusinessRuleException(
                    "BOOK_UNAVAILABLE", "The selected book is not available for borrowing.");
        }

        var student = students.findById(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student %d was not found.".formatted(request.studentId())));

        var record = new BorrowRecord();
        record.setBook(book);
        record.setStudent(student);
        // Borrower details are copied from the student profile, not trusted from the client.
        record.setBorrowerName(student.getName());
        record.setBorrowerEmail(student.getEmail());
        record.setBorrowerPhone(student.getPhone());
        record.setBorrowDate(request.borrowDate());
        book.setAvailable(false);

        return response(records.save(record));
    }

    @Transactional
    public void returnBook(Long id) {
        var record = records.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Borrow record %d was not found.".formatted(id)));

        if (record.getReturnDate() != null) {
            throw new BusinessRuleException(
                    "ALREADY_RETURNED", "This borrow record has already been returned.");
        }

        record.setReturnDate(LocalDate.now());
        record.getBook().setAvailable(true);
    }

    private BorrowRecordResponse response(BorrowRecord record) {
        return new BorrowRecordResponse(
                record.getId(),
                record.getBook().getId(),
                record.getBook().getTitle(),
                record.getStudent().getId(),
                record.getBorrowerName(),
                record.getBorrowerEmail(),
                record.getBorrowerPhone(),
                record.getBorrowDate(),
                record.getReturnDate(),
                record.getReturnDate() == null ? "BORROWED" : "RETURNED");
    }
}
