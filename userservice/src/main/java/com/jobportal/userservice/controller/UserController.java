package com.jobportal.userservice.controller;

import com.jobportal.userservice.dto.*;
import com.jobportal.userservice.exception.ForbiddenException;
import com.jobportal.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_JOB_SEEKER = "JOB_SEEKER";
    private static final String ROLE_RECRUITER = "RECRUITER";

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

    @PutMapping("/me/skills")
    public ResponseEntity<ApiResponse<UserResponse>> updateSkills(
            @Valid @RequestBody SkillsUpdateRequest request,
            @RequestHeader(value = "X-User-Email", required = false) String requesterEmail,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        String email = requireAuthenticated(requesterEmail);
        requireRoleOneOf(requesterRole, ROLE_JOB_SEEKER, ROLE_ADMIN);
        UserResponse response = userService.updateSkills(email, request.getSkills());
        return ResponseEntity.ok(ApiResponse.success("Skills updated successfully", response));
    }

    @PutMapping("/me/experience")
    public ResponseEntity<ApiResponse<UserResponse>> updateExperience(
            @Valid @RequestBody ExperienceUpdateRequest request,
            @RequestHeader(value = "X-User-Email", required = false) String requesterEmail,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        String email = requireAuthenticated(requesterEmail);
        requireRoleOneOf(requesterRole, ROLE_JOB_SEEKER, ROLE_ADMIN);
        UserResponse response = userService.updateExperience(email, request.getExperience());
        return ResponseEntity.ok(ApiResponse.success("Experience updated successfully", response));
    }

    @PutMapping("/me/education")
    public ResponseEntity<ApiResponse<UserResponse>> updateEducation(
            @Valid @RequestBody EducationUpdateRequest request,
            @RequestHeader(value = "X-User-Email", required = false) String requesterEmail,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        String email = requireAuthenticated(requesterEmail);
        requireRoleOneOf(requesterRole, ROLE_JOB_SEEKER, ROLE_ADMIN);
        UserResponse response = userService.updateEducation(email, request.getEducation());
        return ResponseEntity.ok(ApiResponse.success("Education updated successfully", response));
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<ApiResponse<UserResponse>> updatePreferences(
            @Valid @RequestBody JobPreferencesDto preferences,
            @RequestHeader(value = "X-User-Email", required = false) String requesterEmail,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        String email = requireAuthenticated(requesterEmail);
        requireRoleOneOf(requesterRole, ROLE_JOB_SEEKER, ROLE_ADMIN);
        UserResponse response = userService.updatePreferences(email, preferences);
        return ResponseEntity.ok(ApiResponse.success("Preferences updated successfully", response));
    }

    @PutMapping("/me/recruiter")
    public ResponseEntity<ApiResponse<UserResponse>> updateRecruiterProfile(
            @Valid @RequestBody RecruiterProfileRequest request,
            @RequestHeader(value = "X-User-Email", required = false) String requesterEmail,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        String email = requireAuthenticated(requesterEmail);
        requireRoleOneOf(requesterRole, ROLE_RECRUITER, ROLE_ADMIN);
        UserResponse response = userService.updateRecruiterProfile(email, request);
        return ResponseEntity.ok(ApiResponse.success("Recruiter profile updated successfully", response));
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

    private void requireRoleOneOf(String requesterRole, String... allowed) {
        if (requesterRole == null || Arrays.stream(allowed).noneMatch(requesterRole::equals)) {
            throw new ForbiddenException("Role required: one of " + String.join(", ", allowed));
        }
    }
}
