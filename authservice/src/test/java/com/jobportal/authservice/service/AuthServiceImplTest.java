package com.jobportal.authservice.service;

import com.jobportal.authservice.dto.LoginRequest;
import com.jobportal.authservice.dto.LoginResponse;
import com.jobportal.authservice.dto.PasswordUpdateRequest;
import com.jobportal.authservice.entity.Role;
import com.jobportal.authservice.entity.User;
import com.jobportal.authservice.exception.AccountInactiveException;
import com.jobportal.authservice.exception.AuthenticationException;
import com.jobportal.authservice.exception.UserNotFoundException;
import com.jobportal.authservice.repository.UserRepository;
import com.jobportal.authservice.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .email("alice@example.com")
                .password("$2a$10$hashedpassword")
                .role(Role.JOB_SEEKER)
                .isActive(true)
                .build();
    }

    // ---------- login ----------

    @Test
    @DisplayName("login: returns a populated LoginResponse for valid credentials")
    void login_success() {
        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("plaintext")
                .build();

        Instant expiry = Instant.now().plusSeconds(3600);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("plaintext", activeUser.getPassword())).thenReturn(true);
        when(jwtTokenProvider.generateToken(activeUser)).thenReturn("signed.jwt.token");
        when(jwtTokenProvider.getExpirationDate("signed.jwt.token")).thenReturn(expiry);

        LoginResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("signed.jwt.token");
        assertThat(response.getType()).isEqualTo("Bearer");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getUserId()).isEqualTo("alice@example.com");
        assertThat(response.getRole()).isEqualTo(Role.JOB_SEEKER);
        assertThat(response.getExpiresAt()).isEqualTo(expiry);
    }

    @Test
    @DisplayName("login: throws AuthenticationException when email is unknown")
    void login_unknownEmail_throwsAuthenticationException() {
        LoginRequest request = LoginRequest.builder()
                .email("ghost@example.com")
                .password("plaintext")
                .build();

        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Invalid credentials");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtTokenProvider, never()).generateToken(any());
    }

    @Test
    @DisplayName("login: throws AuthenticationException when password does not match")
    void login_wrongPassword_throwsAuthenticationException() {
        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("wrong")
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", activeUser.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Invalid credentials");

        verify(jwtTokenProvider, never()).generateToken(any());
    }

    @Test
    @DisplayName("login: throws AccountInactiveException when user.isActive is false")
    void login_inactiveAccount_throwsAccountInactiveException() {
        User inactive = User.builder()
                .email("alice@example.com")
                .password("$2a$10$hashedpassword")
                .role(Role.JOB_SEEKER)
                .isActive(false)
                .build();

        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("plaintext")
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(inactive));
        when(passwordEncoder.matches("plaintext", inactive.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AccountInactiveException.class)
                .hasMessageContaining("inactive");

        verify(jwtTokenProvider, never()).generateToken(any());
    }

    // ---------- updatePassword ----------

    @Test
    @DisplayName("updatePassword: re-encodes the new password and saves the user")
    void updatePassword_success() {
        PasswordUpdateRequest request = PasswordUpdateRequest.builder()
                .currentPassword("oldPlain")
                .newPassword("newPlainPassword")
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("oldPlain", activeUser.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("newPlainPassword")).thenReturn("$2a$10$newHash");

        authService.updatePassword("alice@example.com", request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("$2a$10$newHash");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
        verify(passwordEncoder).encode("newPlainPassword");
    }

    @Test
    @DisplayName("updatePassword: throws UserNotFoundException when email is unknown")
    void updatePassword_unknownEmail_throwsUserNotFoundException() {
        PasswordUpdateRequest request = PasswordUpdateRequest.builder()
                .currentPassword("oldPlain")
                .newPassword("newPlainPassword")
                .build();

        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.updatePassword("ghost@example.com", request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("ghost@example.com");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updatePassword: throws AuthenticationException when current password is wrong")
    void updatePassword_wrongCurrentPassword_throwsAuthenticationException() {
        PasswordUpdateRequest request = PasswordUpdateRequest.builder()
                .currentPassword("wrongOld")
                .newPassword("newPlainPassword")
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrongOld", activeUser.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.updatePassword("alice@example.com", request))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Current password is incorrect");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
        // sanity: encode for the new password should never be called
        verify(passwordEncoder, never()).encode(eq("newPlainPassword"));
    }
}
