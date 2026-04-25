package com.jobportal.userservice.kafka;

import com.jobportal.kafka_events.UserRegistrationEvent;
import com.jobportal.kafka_events.UserDeleteEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaProducer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducer.class);

    private final KafkaTemplate<String, UserRegistrationEvent> kafkaTemplate;
    private final KafkaTemplate<String, UserDeleteEvent> deleteEventKafkaTemplate;

    @Value("${kafka.topic.user-registration}")
    private String userRegistrationTopic;

    @Value("${kafka.topic.delete-user}")
    private String deleteUserTopic;

    public KafkaProducer(KafkaTemplate<String, UserRegistrationEvent> kafkaTemplate,
                                      KafkaTemplate<String, UserDeleteEvent> deleteEventKafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.deleteEventKafkaTemplate = deleteEventKafkaTemplate;
    }

    public void sendUserRegistrationEvent(UserRegistrationEvent event) {
        logger.info("Sending user registration event for email: {} to topic: {}", event.getEmail(), userRegistrationTopic);

        CompletableFuture<SendResult<String, UserRegistrationEvent>> future =
                kafkaTemplate.send(userRegistrationTopic, event.getEmail(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("User registration event sent successfully. Offset: {}",
                        result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to send user registration event for email: {}", event.getEmail(), ex);
            }
        });
    }

    public void sendUserDeleteEvent(UserDeleteEvent event) {
        logger.info("Sending user delete event for email: {} to topic: {}", event.getEmail(), deleteUserTopic);

        CompletableFuture<SendResult<String, UserDeleteEvent>> future =
                deleteEventKafkaTemplate.send(deleteUserTopic, event.getEmail(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("User delete event sent successfully. Offset: {}",
                        result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to send user delete event for email: {}", event.getEmail(), ex);
            }
        });
    }
}
