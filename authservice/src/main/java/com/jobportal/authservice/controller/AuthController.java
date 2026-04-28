package com.jobportal.authservice.controller;

import com.jobportal.authservice.dto.LoginRequest;
import com.jobportal.authservice.dto.LoginResponse;
import com.jobportal.authservice.dto.PasswordUpdateRequest;
import com.jobportal.authservice.exception.ForbiddenException;
import com.jobportal.authservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/password")
    public ResponseEntity<String> updatePassword(
            @Valid @RequestBody PasswordUpdateRequest request,
            @RequestHeader(value = "X-User-Email", required = false) String requesterEmail) {
        if (requesterEmail == null || requesterEmail.isBlank()) {
            throw new ForbiddenException("Authentication context missing");
        }
        authService.updatePassword(requesterEmail, request);
        return ResponseEntity.ok("update password successful");
    }
}
