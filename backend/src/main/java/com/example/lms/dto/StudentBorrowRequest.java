package com.example.lms.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** A student may select a book and date; their identity comes from the session. */
public record StudentBorrowRequest(@NotNull Long bookId, @NotNull LocalDate borrowDate) { }
