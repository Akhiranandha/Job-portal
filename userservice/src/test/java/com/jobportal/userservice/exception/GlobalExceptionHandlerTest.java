package com.jobportal.userservice.exception;

import com.jobportal.userservice.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
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
    @DisplayName("UserNotFoundException -> 404 with USER_NOT_FOUND code")
    void userNotFound_returns404() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUserNotFoundException(new UserNotFoundException("missing user"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("missing user");
        assertThat(body.error()).isEqualTo("USER_NOT_FOUND");
        assertThat(body.fieldErrors()).isNull();
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("UserAlreadyExistsException -> 409 with USER_ALREADY_EXISTS code")
    void duplicateEmail_returns409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUserAlreadyExistsException(
                        new UserAlreadyExistsException("dup: a@b.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("dup: a@b.com");
        assertThat(body.error()).isEqualTo("USER_ALREADY_EXISTS");
        assertThat(body.fieldErrors()).isNull();
    }

    @Test
    @DisplayName("ForbiddenException -> 403 with FORBIDDEN code")
    void forbidden_returns403() {
        ResponseEntity<ErrorResponse> response =
                handler.handleForbiddenException(new ForbiddenException("no entry"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("no entry");
        assertThat(body.error()).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("UserServiceException -> 400 with USER_SERVICE_ERROR code")
    void userServiceException_returns400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUserServiceException(new UserServiceException("bad input"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("bad input");
        assertThat(body.error()).isEqualTo("USER_SERVICE_ERROR");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException -> 400 with VALIDATION_FAILED and fieldErrors populated")
    void validation_returns400WithFieldErrors() throws Exception {
        Method dummyMethod = DummyTarget.class.getDeclaredMethod("dummy", String.class);
        MethodParameter parameter = new MethodParameter(dummyMethod, 0);

        BindingResult bindingResult = new BeanPropertyBindingResult(new ValidationTarget(), "request");
        bindingResult.rejectValue("email", "NotBlank", "Email is required");
        bindingResult.rejectValue("password", "Size", "Password must be at least 8 characters");

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationExceptions(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("Validation failed");
        assertThat(body.error()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.fieldErrors())
                .containsEntry("email", "Email is required")
                .containsEntry("password", "Password must be at least 8 characters");
    }

    @Test
    @DisplayName("Generic Exception -> 500 with INTERNAL_ERROR code, no underlying message leak")
    void genericException_returns500() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGenericException(new RuntimeException("kaboom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("An unexpected error occurred");
        assertThat(body.message()).doesNotContain("kaboom");
        assertThat(body.error()).isEqualTo("INTERNAL_ERROR");
    }

    @SuppressWarnings("unused")
    private static class DummyTarget {
        public void dummy(String s) {
        }
    }

    @SuppressWarnings("unused")
    public static class ValidationTarget {
        private String email;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
