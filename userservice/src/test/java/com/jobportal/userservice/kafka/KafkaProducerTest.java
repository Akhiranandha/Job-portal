package com.jobportal.userservice.kafka;

import com.jobportal.kafka_events.UserDeleteEvent;
import com.jobportal.kafka_events.UserRegistrationEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaProducerTest {

    @Mock
    private KafkaTemplate<String, UserRegistrationEvent> registrationTemplate;

    @Mock
    private KafkaTemplate<String, UserDeleteEvent> deleteTemplate;

    private KafkaProducer kafkaProducer;

    @BeforeEach
    void setUp() {
        kafkaProducer = new KafkaProducer(registrationTemplate, deleteTemplate);
        // The producer reads topic names from @Value-bound fields. Mirror application.properties values.
        ReflectionTestUtils.setField(kafkaProducer, "userRegistrationTopic", "user-registration");
        ReflectionTestUtils.setField(kafkaProducer, "deleteUserTopic", "delete-user");
    }

    @Test
    @DisplayName("sendUserRegistrationEvent: publishes to 'user-registration' with email key and BCrypt hash payload (NFR-1.3)")
    void sendUserRegistrationEvent_publishesToCorrectTopic() {
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .email("jane.doe@example.com")
                .passwordHash("$2a$10$thisIsHashedNotPlaintext")
                .role("JOB_SEEKER")
                .build();

        when(registrationTemplate.send(eq("user-registration"), eq("jane.doe@example.com"), eq(event)))
                .thenReturn(completedSendResult("user-registration"));

        kafkaProducer.sendUserRegistrationEvent(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UserRegistrationEvent> payloadCaptor =
                ArgumentCaptor.forClass(UserRegistrationEvent.class);

        verify(registrationTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("user-registration");
        assertThat(keyCaptor.getValue()).isEqualTo("jane.doe@example.com");

        UserRegistrationEvent captured = payloadCaptor.getValue();
        assertThat(captured.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(captured.getRole()).isEqualTo("JOB_SEEKER");
        // NFR-1.3: never publish raw passwords. The event field is named passwordHash and must be the hash.
        assertThat(captured.getPasswordHash()).isEqualTo("$2a$10$thisIsHashedNotPlaintext");
        assertThat(captured.getPasswordHash()).startsWith("$2a$"); // BCrypt prefix sanity check
    }

    @Test
    @DisplayName("sendUserDeleteEvent: publishes to 'delete-user' with email key")
    void sendUserDeleteEvent_publishesToCorrectTopic() {
        UserDeleteEvent event = new UserDeleteEvent("jane.doe@example.com");

        when(deleteTemplate.send(eq("delete-user"), eq("jane.doe@example.com"), eq(event)))
                .thenReturn(completedDeleteSendResult("delete-user"));

        kafkaProducer.sendUserDeleteEvent(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UserDeleteEvent> payloadCaptor = ArgumentCaptor.forClass(UserDeleteEvent.class);

        verify(deleteTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("delete-user");
        assertThat(keyCaptor.getValue()).isEqualTo("jane.doe@example.com");
        assertThat(payloadCaptor.getValue().getEmail()).isEqualTo("jane.doe@example.com");
    }

    @Test
    @DisplayName("sendUserRegistrationEvent: failure callback does not throw out of the producer")
    void sendUserRegistrationEvent_failureFutureDoesNotThrow() {
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .email("a@b.com").passwordHash("$2a$10$x").role("JOB_SEEKER").build();

        CompletableFuture<SendResult<String, UserRegistrationEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(registrationTemplate.send(eq("user-registration"), eq("a@b.com"), eq(event))).thenReturn(failed);

        // The producer attaches whenComplete; a failed future must not propagate.
        kafkaProducer.sendUserRegistrationEvent(event);

        verify(registrationTemplate).send("user-registration", "a@b.com", event);
    }

    // --- helpers ---

    private CompletableFuture<SendResult<String, UserRegistrationEvent>> completedSendResult(String topic) {
        RecordMetadata md = new RecordMetadata(new TopicPartition(topic, 0), 0L, 0, 0L, 0, 0);
        SendResult<String, UserRegistrationEvent> result = new SendResult<>(null, md);
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<SendResult<String, UserDeleteEvent>> completedDeleteSendResult(String topic) {
        RecordMetadata md = new RecordMetadata(new TopicPartition(topic, 0), 0L, 0, 0L, 0, 0);
        SendResult<String, UserDeleteEvent> result = new SendResult<>(null, md);
        return CompletableFuture.completedFuture(result);
    }
}
