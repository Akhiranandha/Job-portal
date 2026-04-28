package com.jobportal.authservice.messaging;

import com.jobportal.authservice.entity.Role;
import com.jobportal.authservice.entity.User;
import com.jobportal.authservice.repository.UserRepository;
import com.jobportal.kafka_events.UserRegistrationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationListenerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserRegistrationListener listener;

    private static final String BCRYPT_HASH =
            "$2a$10$abcdefghijklmnopqrstuv0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ.ab";

    @Test
    void handleUserRegistration_persistsUserWithVerbatimHash() {
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .email("alice@example.com")
                .passwordHash(BCRYPT_HASH)
                .role("JOB_SEEKER")
                .build();

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);

        listener.handleUserRegistration(event);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        // NFR-1.3: hash MUST be carried over verbatim (no re-hash)
        assertThat(saved.getPassword()).isEqualTo(BCRYPT_HASH);
        assertThat(saved.getRole()).isEqualTo(Role.JOB_SEEKER);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void handleUserRegistration_defaultsRoleToJobSeekerWhenRoleIsNull() {
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .email("alice@example.com")
                .passwordHash(BCRYPT_HASH)
                .role(null)
                .build();

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);

        listener.handleUserRegistration(event);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.JOB_SEEKER);
    }

    @Test
    void handleUserRegistration_recruiterRole_isMappedCorrectly() {
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .email("rec@example.com")
                .passwordHash(BCRYPT_HASH)
                .role("RECRUITER")
                .build();

        when(userRepository.existsByEmail("rec@example.com")).thenReturn(false);

        listener.handleUserRegistration(event);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.RECRUITER);
    }

    @Test
    void handleUserRegistration_existingEmail_isIdempotentNoSave() {
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .email("alice@example.com")
                .passwordHash(BCRYPT_HASH)
                .role("JOB_SEEKER")
                .build();

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        listener.handleUserRegistration(event);

        verify(userRepository, never()).save(any());
    }

    @Test
    void handleUserRegistration_missingPasswordHash_throws() {
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .email("alice@example.com")
                .passwordHash(null)
                .role("JOB_SEEKER")
                .build();

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);

        assertThatThrownBy(() -> listener.handleUserRegistration(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("passwordHash is required");

        verify(userRepository, never()).save(any());
    }

    @Test
    void handleUserRegistration_blankPasswordHash_throws() {
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .email("alice@example.com")
                .passwordHash("   ")
                .role("JOB_SEEKER")
                .build();

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);

        assertThatThrownBy(() -> listener.handleUserRegistration(event))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any());
    }
}
