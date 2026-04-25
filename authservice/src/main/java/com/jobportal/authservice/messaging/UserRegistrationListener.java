package com.jobportal.authservice.messaging;

import com.jobportal.kafka_events.UserRegistrationEvent;
import com.jobportal.authservice.entity.Role;
import com.jobportal.authservice.entity.User;
import com.jobportal.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegistrationListener {

    private final UserRepository userRepository;

    @KafkaListener(topics = "${kafka.topic.user-registration}")
    @Transactional
    public void handleUserRegistration(UserRegistrationEvent event) {
        log.info("Received user registration event for email: {}", event.getEmail());

        if (userRepository.existsByEmail(event.getEmail())) {
            log.warn("User with email {} already exists, skipping registration", event.getEmail());
            return;
        }

        if (event.getPasswordHash() == null || event.getPasswordHash().isBlank()) {
            log.error("Registration event for {} missing passwordHash; rejecting", event.getEmail());
            throw new IllegalArgumentException("UserRegistrationEvent.passwordHash is required");
        }

        User user = User.builder()
                .email(event.getEmail())
                .password(event.getPasswordHash())
                .role(event.getRole() != null ? Role.valueOf(event.getRole()) : Role.JOB_SEEKER)
                .isActive(true)
                .build();

        userRepository.save(user);
        log.info("User with email {} registered successfully", event.getEmail());
    }
}
