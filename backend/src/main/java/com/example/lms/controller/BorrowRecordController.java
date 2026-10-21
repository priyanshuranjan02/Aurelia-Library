package com.example.lms.controller;

import com.example.lms.dto.BorrowRecordResponse;
import com.example.lms.dto.BorrowRequest;
import com.example.lms.dto.StudentBorrowRequest;
import com.example.lms.service.BorrowRecordService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/borrow-records")
public class BorrowRecordController {
    private final BorrowRecordService service;

    public BorrowRecordController(BorrowRecordService service) {
        this.service = service;
    }

    /** Staff view: all borrow records, optionally filtered by status. */
    @GetMapping
    public List<BorrowRecordResponse> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    /** Student view: only the current student's own borrowing history. */
    @GetMapping("/mine")
    public List<BorrowRecordResponse> mine(@RequestParam(required = false) String status, Authentication authentication) {
        return service.listForStudent(authentication.getName(), status);
    }

    /** Staff-created borrow record, for a selected student. */
    @PostMapping
    public ResponseEntity<BorrowRecordResponse> borrow(@Valid @RequestBody BorrowRequest request) {
        var created = service.borrow(request);
        return ResponseEntity.created(URI.create("/api/borrow-records/" + created.id())).body(created);
    }

    /** Student self-borrow; student identity is resolved from the session, never the request body. */
    @PostMapping("/mine")
    public ResponseEntity<BorrowRecordResponse> borrowForCurrentStudent(
            @Valid @RequestBody StudentBorrowRequest request, Authentication authentication) {
        var created = service.borrowForStudent(authentication.getName(), request);
        return ResponseEntity.created(URI.create("/api/borrow-records/" + created.id())).body(created);
    }

    @PostMapping("/{id}/return")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void returnBook(@PathVariable Long id) {
        service.returnBook(id);
    }
}
