package com.jobportal.authservice.messaging;

import com.jobportal.kafka_events.UserDeleteEvent;
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
public class UserDeleteListener {

    private final UserRepository userRepository;

    @KafkaListener(topics = "${kafka.topic.delete-user}")
    @Transactional
    public void handleUserDelete(UserDeleteEvent event) {
        log.info("Received user delete event for email: {}", event.getEmail());

        User user = userRepository.findByEmail(event.getEmail()).orElse(null);

        if (user == null) {
            log.warn("User with email {} not found, skipping delete", event.getEmail());
            return;
        }

        user.setActive(false);
        userRepository.save(user);
        log.info("User with email {} soft deleted (isActive set to false)", event.getEmail());
    }
}
