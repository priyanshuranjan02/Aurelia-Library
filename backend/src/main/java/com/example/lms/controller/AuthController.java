package com.example.lms.controller;

import com.example.lms.dto.AuthenticatedUserResponse;
import com.example.lms.dto.StudentRequest;
import com.example.lms.dto.StudentResponse;
import com.example.lms.service.AuthService;
import com.example.lms.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final StudentService students;

    public AuthController(AuthService service, StudentService students) {
        this.service = service;
        this.students = students;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    /** Public self-service signup. StudentService always assigns the STUDENT role. */
    @PostMapping("/register")
    public ResponseEntity<StudentResponse> register(@Valid @RequestBody StudentRequest request) {
        StudentResponse created = students.create(request);
        return ResponseEntity.created(URI.create("/api/students/" + created.id())).body(created);
    }

    @PostMapping("/login")
    public AuthenticatedUserResponse login(@Valid @RequestBody com.example.lms.dto.LoginRequest request,
            HttpServletRequest servletRequest) {
        return service.login(request, servletRequest);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        service.logout(request);
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse me(Authentication authentication) {
        return service.current(authentication.getName());
    }
}
