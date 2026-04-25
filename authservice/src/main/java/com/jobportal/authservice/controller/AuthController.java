package com.jobportal.authservice.controller;

import com.jobportal.authservice.dto.LoginRequest;
import com.jobportal.authservice.dto.LoginResponse;
import com.jobportal.authservice.dto.PasswordUpdateRequest;
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
    public ResponseEntity<String> updatePassword(@Valid @RequestBody PasswordUpdateRequest request) {
        authService.updatePassword(request);
        return ResponseEntity.ok("update password successful");
    }
}
