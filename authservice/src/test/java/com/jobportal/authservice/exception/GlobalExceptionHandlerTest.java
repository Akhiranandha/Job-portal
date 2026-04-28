package com.jobportal.authservice.exception;

import com.jobportal.authservice.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleAuthenticationException_returns401WithInvalidCredentials() {
        AuthenticationException ex = new AuthenticationException("Invalid credentials");

        ResponseEntity<ErrorResponse> response = handler.handleAuthenticationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("Invalid credentials");
        assertThat(body.error()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(body.fieldErrors()).isNull();
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    void handleAccountInactiveException_returns403WithAccountInactive() {
        AccountInactiveException ex = new AccountInactiveException("Account is inactive");

        ResponseEntity<ErrorResponse> response = handler.handleAccountInactiveException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("Account is inactive");
        assertThat(body.error()).isEqualTo("ACCOUNT_INACTIVE");
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    void handleUserNotFoundException_returns404WithUserNotFound() {
        UserNotFoundException ex = new UserNotFoundException("User not found with email: ghost@example.com");

        ResponseEntity<ErrorResponse> response = handler.handleUserNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("User not found with email: ghost@example.com");
        assertThat(body.error()).isEqualTo("USER_NOT_FOUND");
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    void handleForbiddenException_returns403WithForbidden() {
        ForbiddenException ex = new ForbiddenException("Authentication context missing");

        ResponseEntity<ErrorResponse> response = handler.handleForbiddenException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("Authentication context missing");
        assertThat(body.error()).isEqualTo("FORBIDDEN");
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    void handleGenericException_returns500WithInternalErrorAndNoLeak() {
        Exception ex = new RuntimeException("boom: should not leak to client");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("An unexpected error occurred");
        assertThat(body.message()).doesNotContain("boom");
        assertThat(body.error()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    void handleValidationExceptions_returns400WithValidationFailedAndFieldErrors() throws Exception {
        Object dummyTarget = new Object();
        BindingResult bindingResult = new BeanPropertyBindingResult(dummyTarget, "loginRequest");
        bindingResult.addError(new FieldError("loginRequest", "email", "Email is required"));
        bindingResult.addError(new FieldError("loginRequest", "password", "Password is required"));

        Method m = GlobalExceptionHandlerTest.class.getDeclaredMethod(
                "handleValidationExceptions_returns400WithValidationFailedAndFieldErrors");
        MethodParameter param = new MethodParameter(m, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationExceptions(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("Validation failed");
        assertThat(body.error()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.fieldErrors())
                .containsEntry("email", "Email is required")
                .containsEntry("password", "Password is required");
    }
}
