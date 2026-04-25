package com.jobportal.userservice.controller;

import com.jobportal.userservice.dto.*;
import com.jobportal.userservice.exception.ForbiddenException;
import com.jobportal.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/public/register")
    public ResponseEntity<ApiResponse<UserResponse>> registerUser(
            @Valid @RequestBody UserRegistrationRequest request) {
        UserResponse userResponse = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", userResponse));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @RequestHeader(value = "X-User-Email", required = false) String requesterEmail) {
        String email = requireAuthenticated(requesterEmail);
        UserResponse userResponse = userService.getUserByEmail(email);
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", userResponse));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers(
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        requireAdmin(requesterRole);
        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateCurrentUser(
            @Valid @RequestBody UserUpdateRequest request,
            @RequestHeader(value = "X-User-Email", required = false) String requesterEmail) {
        String email = requireAuthenticated(requesterEmail);
        UserResponse userResponse = userService.updateUser(email, request);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", userResponse));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteCurrentUser(
            @RequestHeader(value = "X-User-Email", required = false) String requesterEmail) {
        String email = requireAuthenticated(requesterEmail);
        userService.deleteUser(email);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }

    private String requireAuthenticated(String requesterEmail) {
        if (requesterEmail == null || requesterEmail.isBlank()) {
            throw new ForbiddenException("Authentication context missing");
        }
        return requesterEmail;
    }

    private void requireAdmin(String requesterRole) {
        if (!ROLE_ADMIN.equals(requesterRole)) {
            throw new ForbiddenException("ADMIN role required");
        }
    }
}
