package com.jobportal.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.userservice.dto.UserRegistrationRequest;
import com.jobportal.userservice.dto.UserResponse;
import com.jobportal.userservice.dto.UserUpdateRequest;
import com.jobportal.userservice.entity.User;
import com.jobportal.userservice.exception.ForbiddenException;
import com.jobportal.userservice.exception.UserAlreadyExistsException;
import com.jobportal.userservice.exception.UserNotFoundException;
import com.jobportal.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    private UserRegistrationRequest validRegistration;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        validRegistration = new UserRegistrationRequest();
        validRegistration.setEmail("jane.doe@example.com");
        validRegistration.setPassword("plaintext1");
        validRegistration.setRole(User.Role.JOB_SEEKER);
        validRegistration.setFirstName("Jane");
        validRegistration.setLastName("Doe");

        userResponse = new UserResponse();
        userResponse.setEmail("jane.doe@example.com");
        userResponse.setFirstName("Jane");
        userResponse.setLastName("Doe");
        userResponse.setRole(User.Role.JOB_SEEKER);
        userResponse.setIsActive(true);
        userResponse.setIsEmailVerified(false);
    }

    // ---------- POST /api/users/public/register ----------

    @Test
    @DisplayName("POST /api/users/public/register: 201 with ApiResponse success envelope on happy path")
    void register_success() throws Exception {
        when(userService.registerUser(any(UserRegistrationRequest.class))).thenReturn(userResponse);

        mockMvc.perform(post("/api/users/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegistration)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.data.email").value("jane.doe@example.com"))
                .andExpect(jsonPath("$.data.firstName").value("Jane"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/users/public/register: 400 ErrorResponse with VALIDATION_FAILED + fieldErrors")
    void register_validationFailure() throws Exception {
        UserRegistrationRequest invalid = new UserRegistrationRequest();
        // missing email, password, role, firstName, lastName

        mockMvc.perform(post("/api/users/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isMap())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.lastName").exists())
                .andExpect(jsonPath("$.fieldErrors.role").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(userService, never()).registerUser(any());
    }

    @Test
    @DisplayName("POST /api/users/public/register: 409 ErrorResponse with USER_ALREADY_EXISTS")
    void register_duplicateEmail() throws Exception {
        when(userService.registerUser(any(UserRegistrationRequest.class)))
                .thenThrow(new UserAlreadyExistsException("User with email already exists: jane.doe@example.com"));

        mockMvc.perform(post("/api/users/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegistration)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User with email already exists: jane.doe@example.com"))
                .andExpect(jsonPath("$.error").value("USER_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ---------- GET /api/users/me ----------

    @Test
    @DisplayName("GET /api/users/me: 200 with user when X-User-Email present")
    void getMe_success() throws Exception {
        when(userService.getUserByEmail("jane.doe@example.com")).thenReturn(userResponse);

        mockMvc.perform(get("/api/users/me")
                        .header("X-User-Email", "jane.doe@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User retrieved successfully"))
                .andExpect(jsonPath("$.data.email").value("jane.doe@example.com"));
    }

    @Test
    @DisplayName("GET /api/users/me: 403 ErrorResponse with FORBIDDEN when X-User-Email missing")
    void getMe_missingHeader() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication context missing"))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(userService, never()).getUserByEmail(any());
    }

    @Test
    @DisplayName("GET /api/users/me: 403 when X-User-Email is blank")
    void getMe_blankHeader() throws Exception {
        mockMvc.perform(get("/api/users/me").header("X-User-Email", "   "))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Authentication context missing"))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/users/me: 404 ErrorResponse with USER_NOT_FOUND when service reports user not found")
    void getMe_userNotFound() throws Exception {
        when(userService.getUserByEmail("jane.doe@example.com"))
                .thenThrow(new UserNotFoundException("User not found with email: jane.doe@example.com"));

        mockMvc.perform(get("/api/users/me").header("X-User-Email", "jane.doe@example.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }

    // ---------- GET /api/users (admin only) ----------

    @Test
    @DisplayName("GET /api/users: 200 when X-User-Role is ADMIN")
    void getAll_admin() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(userResponse));

        mockMvc.perform(get("/api/users").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Users retrieved successfully"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].email").value("jane.doe@example.com"));
    }

    @Test
    @DisplayName("GET /api/users: 403 ErrorResponse with FORBIDDEN when X-User-Role is not ADMIN")
    void getAll_nonAdmin() throws Exception {
        mockMvc.perform(get("/api/users").header("X-User-Role", "JOB_SEEKER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("ADMIN role required"))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(userService, never()).getAllUsers();
    }

    @Test
    @DisplayName("GET /api/users: 403 when X-User-Role header is missing entirely")
    void getAll_missingRole() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("ADMIN role required"))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(userService, never()).getAllUsers();
    }

    @Test
    @DisplayName("GET /api/users: case-sensitive — lowercase 'admin' is rejected")
    void getAll_caseSensitiveRole() throws Exception {
        mockMvc.perform(get("/api/users").header("X-User-Role", "admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // ---------- PUT /api/users/me ----------

    @Test
    @DisplayName("PUT /api/users/me: 200 happy path")
    void updateMe_success() throws Exception {
        UserUpdateRequest update = new UserUpdateRequest();
        update.setFirstName("Janet");

        when(userService.updateUser(eq("jane.doe@example.com"), any(UserUpdateRequest.class)))
                .thenReturn(userResponse);

        mockMvc.perform(put("/api/users/me")
                        .header("X-User-Email", "jane.doe@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User updated successfully"))
                .andExpect(jsonPath("$.data.email").value("jane.doe@example.com"));
    }

    @Test
    @DisplayName("PUT /api/users/me: 403 with FORBIDDEN when X-User-Email missing")
    void updateMe_missingEmail() throws Exception {
        UserUpdateRequest update = new UserUpdateRequest();
        update.setFirstName("Janet");

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Authentication context missing"))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(userService, never()).updateUser(any(), any());
    }

    @Test
    @DisplayName("PUT /api/users/me: 400 VALIDATION_FAILED with fieldErrors when dateOfBirth is in the future")
    void updateMe_validationFailure() throws Exception {
        String body = "{\"firstName\":\"Janet\",\"dateOfBirth\":\"3000-01-01\"}";

        mockMvc.perform(put("/api/users/me")
                        .header("X-User-Email", "jane.doe@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").exists());

        verify(userService, never()).updateUser(any(), any());
    }

    // ---------- DELETE /api/users/me ----------

    @Test
    @DisplayName("DELETE /api/users/me: 200 happy path with null data")
    void deleteMe_success() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .header("X-User-Email", "jane.doe@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User deleted successfully"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(userService).deleteUser("jane.doe@example.com");
    }

    @Test
    @DisplayName("DELETE /api/users/me: 403 with FORBIDDEN when X-User-Email missing")
    void deleteMe_missingEmail() throws Exception {
        mockMvc.perform(delete("/api/users/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Authentication context missing"))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(userService, never()).deleteUser(any());
    }

    @Test
    @DisplayName("DELETE /api/users/me: surfaces UserNotFoundException as 404 with USER_NOT_FOUND")
    void deleteMe_notFound() throws Exception {
        doThrow(new UserNotFoundException("User not found with email: jane.doe@example.com"))
                .when(userService).deleteUser("jane.doe@example.com");

        mockMvc.perform(delete("/api/users/me")
                        .header("X-User-Email", "jane.doe@example.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("Service-thrown ForbiddenException is surfaced as 403 with FORBIDDEN")
    void serviceLayerForbidden_handled() throws Exception {
        when(userService.getUserByEmail("jane.doe@example.com"))
                .thenThrow(new ForbiddenException("Not allowed"));

        mockMvc.perform(get("/api/users/me").header("X-User-Email", "jane.doe@example.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Not allowed"))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
