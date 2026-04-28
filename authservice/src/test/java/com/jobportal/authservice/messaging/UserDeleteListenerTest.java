package com.jobportal.authservice.messaging;

import com.jobportal.authservice.entity.Role;
import com.jobportal.authservice.entity.User;
import com.jobportal.authservice.repository.UserRepository;
import com.jobportal.kafka_events.UserDeleteEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDeleteListenerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDeleteListener listener;

    @Test
    void handleUserDelete_softDeletesMatchingUser() {
        User active = User.builder()
                .email("alice@example.com")
                .password("$2a$10$hash")
                .role(Role.JOB_SEEKER)
                .isActive(true)
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(active));

        listener.handleUserDelete(new UserDeleteEvent("alice@example.com"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.isActive()).isFalse();
    }

    @Test
    void handleUserDelete_unknownEmail_isNoOp() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        listener.handleUserDelete(new UserDeleteEvent("ghost@example.com"));

        verify(userRepository, never()).save(any());
    }
}
