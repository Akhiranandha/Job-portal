package com.jobportal.authservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.authservice.dto.LoginRequest;
import com.jobportal.authservice.dto.LoginResponse;
import com.jobportal.authservice.dto.PasswordUpdateRequest;
import com.jobportal.authservice.entity.Role;
import com.jobportal.authservice.exception.AuthenticationException;
import com.jobportal.authservice.exception.UserNotFoundException;
import com.jobportal.authservice.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    // ---------- POST /auth/login ----------

    @Test
    void login_success_returns200WithLoginResponse() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("plaintext")
                .build();

        Instant expiry = Instant.parse("2026-04-26T10:00:00Z");
        LoginResponse response = LoginResponse.builder()
                .token("signed.jwt.token")
                .type("Bearer")
                .userId("alice@example.com")
                .email("alice@example.com")
                .role(Role.JOB_SEEKER)
                .expiresAt(expiry)
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed.jwt.token"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.userId").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("JOB_SEEKER"));
    }

    @Test
    void login_missingFields_returns400WithValidationEnvelope() throws Exception {
        LoginRequest invalid = LoginRequest.builder()
                .email("")
                .password("")
                .build();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void login_authenticationFailure_returns401WithInvalidCredentials() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("wrong")
                .build();

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new AuthenticationException("Invalid credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---------- PUT /auth/password ----------

    @Test
    void updatePassword_success_returns200WithPlainTextBody() throws Exception {
        PasswordUpdateRequest request = PasswordUpdateRequest.builder()
                .email("alice@example.com")
                .currentPassword("oldPlain")
                .newPassword("newPlainPassword")
                .build();

        mockMvc.perform(put("/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("update password successful"));
    }

    @Test
    void updatePassword_wrongCurrentPassword_returns401WithInvalidCredentials() throws Exception {
        PasswordUpdateRequest request = PasswordUpdateRequest.builder()
                .email("alice@example.com")
                .currentPassword("wrongOld")
                .newPassword("newPlainPassword")
                .build();

        doThrow(new AuthenticationException("Current password is incorrect"))
                .when(authService).updatePassword(any(PasswordUpdateRequest.class));

        mockMvc.perform(put("/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Current password is incorrect"))
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void updatePassword_userNotFound_returns404WithUserNotFound() throws Exception {
        PasswordUpdateRequest request = PasswordUpdateRequest.builder()
                .email("ghost@example.com")
                .currentPassword("oldPlain")
                .newPassword("newPlainPassword")
                .build();

        doThrow(new UserNotFoundException("User not found with email: ghost@example.com"))
                .when(authService).updatePassword(any(PasswordUpdateRequest.class));

        mockMvc.perform(put("/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User not found with email: ghost@example.com"))
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
