package com.example.lms.exception;

import com.example.lms.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiErrorResponse notFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return error(404, "NOT_FOUND", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiErrorResponse conflict(ConflictException exception, HttpServletRequest request) {
        return error(409, "CONFLICT", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse business(BusinessRuleException exception, HttpServletRequest request) {
        return error(400, exception.getCode(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse invalid(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var fields = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> new ApiErrorResponse.FieldError(field.getField(), field.getDefaultMessage())).toList();
        return error(400, "VALIDATION_ERROR", "Request validation failed.", request, fields);
    }

    @ExceptionHandler(MailNotConfiguredException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiErrorResponse mailNotConfigured(MailNotConfiguredException exception, HttpServletRequest request) {
        return error(503, "INVITATION_EMAIL_NOT_CONFIGURED", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MailException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiErrorResponse mailFailed(MailException exception, HttpServletRequest request) {
        return error(503, "INVITATION_EMAIL_FAILED",
                "The invitation email could not be sent. Check the SMTP configuration and try again.", request, List.of());
    }

    private ApiErrorResponse error(int status, String code, String message, HttpServletRequest request,
                                   List<ApiErrorResponse.FieldError> fields) {
        return new ApiErrorResponse(Instant.now(), status, code, message, request.getRequestURI(), fields);
    }
}
