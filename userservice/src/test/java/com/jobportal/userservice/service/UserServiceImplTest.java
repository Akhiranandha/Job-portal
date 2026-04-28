package com.jobportal.userservice.service;

import com.jobportal.kafka_events.ProfileUpdatedEvent;
import com.jobportal.kafka_events.UserDeleteEvent;
import com.jobportal.kafka_events.UserRegistrationEvent;
import com.jobportal.userservice.dto.EducationEntry;
import com.jobportal.userservice.dto.ExperienceEntry;
import com.jobportal.userservice.dto.JobPreferencesDto;
import com.jobportal.userservice.dto.RecruiterProfileRequest;
import com.jobportal.userservice.dto.UserRegistrationRequest;
import com.jobportal.userservice.dto.UserResponse;
import com.jobportal.userservice.dto.UserUpdateRequest;
import com.jobportal.userservice.entity.User;
import com.jobportal.userservice.exception.UserAlreadyExistsException;
import com.jobportal.userservice.exception.UserNotFoundException;
import com.jobportal.userservice.exception.UserServiceException;
import com.jobportal.userservice.kafka.KafkaProducer;
import com.jobportal.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// ModelMapper has overloaded map(...) methods (one returns D, one is void). Strict stubbing flags
// the void overload as a "different args" match against the typed-class overload's stub. Lenient is
// safe here since each test still asserts behaviour with explicit verify() calls.
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KafkaProducer kafkaProducer;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private UserRegistrationRequest registrationRequest;
    private User mappedUserFromRequest;
    private User savedUser;
    private UserResponse expectedResponse;

    @BeforeEach
    void setUp() {
        registrationRequest = new UserRegistrationRequest();
        registrationRequest.setEmail("jane.doe@example.com");
        registrationRequest.setPassword("plaintextSecret123");
        registrationRequest.setRole(User.Role.JOB_SEEKER);
        registrationRequest.setFirstName("Jane");
        registrationRequest.setLastName("Doe");

        mappedUserFromRequest = new User();
        mappedUserFromRequest.setEmail("jane.doe@example.com");
        mappedUserFromRequest.setFirstName("Jane");
        mappedUserFromRequest.setLastName("Doe");
        mappedUserFromRequest.setRole(User.Role.JOB_SEEKER);

        savedUser = new User();
        savedUser.setEmail("jane.doe@example.com");
        savedUser.setFirstName("Jane");
        savedUser.setLastName("Doe");
        savedUser.setRole(User.Role.JOB_SEEKER);
        savedUser.setIsActive(true);
        savedUser.setIsEmailVerified(false);

        expectedResponse = new UserResponse();
        expectedResponse.setEmail("jane.doe@example.com");
        expectedResponse.setFirstName("Jane");
        expectedResponse.setLastName("Doe");
        expectedResponse.setRole(User.Role.JOB_SEEKER);
        expectedResponse.setIsActive(true);
        expectedResponse.setIsEmailVerified(false);
    }

    // ---------- registerUser ----------

    @Test
    @DisplayName("registerUser: success path saves with isActive=true, isEmailVerified=false and publishes hashed credentials")
    void registerUser_success_savesUserAndPublishesEventWithBcryptHash() {
        when(userRepository.existsByEmail(registrationRequest.getEmail())).thenReturn(false);
        when(modelMapper.map(registrationRequest, User.class)).thenReturn(mappedUserFromRequest);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(passwordEncoder.encode("plaintextSecret123"))
                .thenReturn("$2a$10$abcdefghijklmnopqrstuvHASHED");
        when(modelMapper.map(savedUser, UserResponse.class)).thenReturn(expectedResponse);

        UserResponse result = userService.registerUser(registrationRequest);

        // Saved entity should have flags set.
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User persisted = userCaptor.getValue();
        assertThat(persisted.getIsActive()).isTrue();
        assertThat(persisted.getIsEmailVerified()).isFalse();
        assertThat(persisted.getEmail()).isEqualTo("jane.doe@example.com");

        // Kafka event published with hashed (NOT raw) password.
        ArgumentCaptor<UserRegistrationEvent> eventCaptor =
                ArgumentCaptor.forClass(UserRegistrationEvent.class);
        verify(kafkaProducer).sendUserRegistrationEvent(eventCaptor.capture());
        UserRegistrationEvent event = eventCaptor.getValue();
        assertThat(event.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(event.getRole()).isEqualTo("JOB_SEEKER");
        assertThat(event.getPasswordHash())
                .isEqualTo("$2a$10$abcdefghijklmnopqrstuvHASHED")
                .isNotEqualTo("plaintextSecret123");
        assertThat(event.getPasswordHash()).doesNotContain("plaintextSecret123");

        assertThat(result).isSameAs(expectedResponse);
    }

    @Test
    @DisplayName("registerUser: ADMIN role is rejected with UserServiceException and never touches the repo")
    void registerUser_adminRoleRejected() {
        registrationRequest.setRole(User.Role.ADMIN);

        assertThatThrownBy(() -> userService.registerUser(registrationRequest))
                .isInstanceOf(UserServiceException.class)
                .hasMessageContaining("ADMIN");

        verifyNoInteractions(userRepository, kafkaProducer, modelMapper, passwordEncoder);
    }

    @Test
    @DisplayName("registerUser: duplicate email rejected with UserAlreadyExistsException; nothing persisted or published")
    void registerUser_duplicateEmail() {
        when(userRepository.existsByEmail(registrationRequest.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser(registrationRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("jane.doe@example.com");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(kafkaProducer, passwordEncoder);
    }

    // ---------- getUserByEmail ----------

    @Test
    @DisplayName("getUserByEmail: returns mapped DTO when active user exists")
    void getUserByEmail_success() {
        when(userRepository.findByEmailAndIsActiveTrue("jane.doe@example.com"))
                .thenReturn(Optional.of(savedUser));
        when(modelMapper.map(savedUser, UserResponse.class)).thenReturn(expectedResponse);

        UserResponse result = userService.getUserByEmail("jane.doe@example.com");

        assertThat(result).isSameAs(expectedResponse);
    }

    @Test
    @DisplayName("getUserByEmail: throws UserNotFoundException when user is missing or soft-deleted")
    void getUserByEmail_notFoundCoversSoftDelete() {
        when(userRepository.findByEmailAndIsActiveTrue("ghost@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByEmail("ghost@example.com"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("ghost@example.com");
    }

    // ---------- getAllUsers ----------

    @Test
    @DisplayName("getAllUsers: maps every active user to a UserResponse")
    void getAllUsers_returnsAllActive() {
        User other = new User();
        other.setEmail("john@example.com");
        UserResponse otherResponse = new UserResponse();
        otherResponse.setEmail("john@example.com");

        when(userRepository.findByIsActiveTrue()).thenReturn(Arrays.asList(savedUser, other));
        when(modelMapper.map(savedUser, UserResponse.class)).thenReturn(expectedResponse);
        when(modelMapper.map(other, UserResponse.class)).thenReturn(otherResponse);

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).containsExactly(expectedResponse, otherResponse);
    }

    @Test
    @DisplayName("getAllUsers: returns empty list when no active users")
    void getAllUsers_emptyList() {
        when(userRepository.findByIsActiveTrue()).thenReturn(List.of());

        assertThat(userService.getAllUsers()).isEmpty();
        verify(modelMapper, never()).map(any(), eq(UserResponse.class));
    }

    // ---------- updateUser ----------

    @Test
    @DisplayName("updateUser: success applies mapper, saves, returns response")
    void updateUser_success() {
        UserUpdateRequest update = new UserUpdateRequest();
        update.setFirstName("Janet");

        when(userRepository.findByEmailAndIsActiveTrue("jane.doe@example.com"))
                .thenReturn(Optional.of(savedUser));
        when(userRepository.save(savedUser)).thenReturn(savedUser);
        when(modelMapper.map(savedUser, UserResponse.class)).thenReturn(expectedResponse);

        UserResponse result = userService.updateUser("jane.doe@example.com", update);

        verify(modelMapper).map(update, savedUser);
        verify(userRepository).save(savedUser);
        assertThat(result).isSameAs(expectedResponse);
    }

    @Test
    @DisplayName("updateUser: throws UserNotFoundException when user missing")
    void updateUser_notFound() {
        when(userRepository.findByEmailAndIsActiveTrue("ghost@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser("ghost@example.com", new UserUpdateRequest()))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    // ---------- deleteUser ----------

    @Test
    @DisplayName("deleteUser: soft-deletes (isActive=false), saves, publishes UserDeleteEvent")
    void deleteUser_success() {
        when(userRepository.findByEmailAndIsActiveTrue("jane.doe@example.com"))
                .thenReturn(Optional.of(savedUser));

        userService.deleteUser("jane.doe@example.com");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getIsActive()).isFalse();

        ArgumentCaptor<UserDeleteEvent> eventCaptor = ArgumentCaptor.forClass(UserDeleteEvent.class);
        verify(kafkaProducer).sendUserDeleteEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEmail()).isEqualTo("jane.doe@example.com");
    }

    @Test
    @DisplayName("deleteUser: throws UserNotFoundException when user missing; nothing saved or published")
    void deleteUser_notFound() {
        when(userRepository.findByEmailAndIsActiveTrue("ghost@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser("ghost@example.com"))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).save(any());
        verify(kafkaProducer, never()).sendUserDeleteEvent(any());
    }

    @Test
    @DisplayName("deleteUser: only one save and one event publish per call")
    void deleteUser_singleInvocations() {
        when(userRepository.findByEmailAndIsActiveTrue("jane.doe@example.com"))
                .thenReturn(Optional.of(savedUser));

        userService.deleteUser("jane.doe@example.com");

        verify(userRepository, times(1)).save(any(User.class));
        verify(kafkaProducer, times(1)).sendUserDeleteEvent(any(UserDeleteEvent.class));
    }

    // ---------- updateSkills ----------

    @Test
    @DisplayName("updateSkills: saves new skills and publishes ProfileUpdatedEvent")
    void updateSkills_success() {
        when(userRepository.findByEmailAndIsActiveTrue("jane.doe@example.com"))
                .thenReturn(Optional.of(savedUser));
        when(userRepository.save(savedUser)).thenReturn(savedUser);
        when(modelMapper.map(savedUser, UserResponse.class)).thenReturn(expectedResponse);

        UserResponse result = userService.updateSkills("jane.doe@example.com", List.of("Java", "Kafka"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getSkills()).containsExactly("Java", "Kafka");

        ArgumentCaptor<ProfileUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(ProfileUpdatedEvent.class);
        verify(kafkaProducer).sendProfileUpdatedEvent(eventCaptor.capture());
        ProfileUpdatedEvent event = eventCaptor.getValue();
        assertThat(event.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(event.getRole()).isEqualTo("JOB_SEEKER");
        assertThat(event.getSkills()).containsExactly("Java", "Kafka");
        assertThat(event.getUpdatedAt()).isNotNull();

        assertThat(result).isSameAs(expectedResponse);
    }

    @Test
    @DisplayName("updateSkills: throws UserNotFoundException; no save or event")
    void updateSkills_notFound() {
        when(userRepository.findByEmailAndIsActiveTrue("ghost@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateSkills("ghost@example.com", List.of("Java")))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).save(any());
        verify(kafkaProducer, never()).sendProfileUpdatedEvent(any());
    }

    @Test
    @DisplayName("updateSkills: empty list clears skills (passed through)")
    void updateSkills_emptyList() {
        when(userRepository.findByEmailAndIsActiveTrue("jane.doe@example.com"))
                .thenReturn(Optional.of(savedUser));
        when(userRepository.save(savedUser)).thenReturn(savedUser);
        when(modelMapper.map(savedUser, UserResponse.class)).thenReturn(expectedResponse);

        userService.updateSkills("jane.doe@example.com", List.of());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getSkills()).isEmpty();
    }

    // ---------- updateExperience ----------

    @Test
    @DisplayName("updateExperience: persists entry as Map, publishes event")
    void updateExperience_success() {
        ExperienceEntry entry = ExperienceEntry.builder()
                .company("Acme")
                .role("Backend Engineer")
                .startDate("2022-01")
                .endDate("2024-06")
                .description("Built event-driven payment service")
                .build();

        when(userRepository.findByEmailAndIsActiveTrue("jane.doe@example.com"))
                .thenReturn(Optional.of(savedUser));
        when(userRepository.save(savedUser)).thenReturn(savedUser);
        when(modelMapper.map(savedUser, UserResponse.class)).thenReturn(expectedResponse);

        userService.updateExperience("jane.doe@example.com", List.of(entry));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getExperience()).hasSize(1);
        assertThat(userCaptor.getValue().getExperience().get(0))
                .containsEntry("company", "Acme")
                .containsEntry("role", "Backend Engineer")
                .containsEntry("startDate", "2022-01");

        verify(kafkaProducer).sendProfileUpdatedEvent(any(ProfileUpdatedEvent.class));
    }

    // ---------- updateEducation ----------

    @Test
    @DisplayName("updateEducation: persists entry as Map, publishes event")
    void updateEducation_success() {
        EducationEntry entry = EducationEntry.builder()
                .institution("IIT Bombay")
                .degree("B.Tech")
                .field("Computer Science")
                .startYear(2018)
                .endYear(2022)
                .build();

        when(userRepository.findByEmailAndIsActiveTrue("jane.doe@example.com"))
                .thenReturn(Optional.of(savedUser));
        when(userRepository.save(savedUser)).thenReturn(savedUser);
        when(modelMapper.map(savedUser, UserResponse.class)).thenReturn(expectedResponse);

        userService.updateEducation("jane.doe@example.com", List.of(entry));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEducation()).hasSize(1);
        assertThat(userCaptor.getValue().getEducation().get(0))
                .containsEntry("institution", "IIT Bombay")
                .containsEntry("startYear", 2018);

        verify(kafkaProducer).sendProfileUpdatedEvent(any(ProfileUpdatedEvent.class));
    }

    // ---------- updatePreferences ----------

    @Test
    @DisplayName("updatePreferences: persists Map with INR default when currency null")
    void updatePreferences_currencyDefault() {
        JobPreferencesDto prefs = JobPreferencesDto.builder()
                .locations(List.of("Bangalore"))
                .salaryMin(100L)
                .salaryMax(200L)
                .remote(true)
                .employmentTypes(List.of("FULL_TIME"))
                .build();

        when(userRepository.findByEmailAndIsActiveTrue("jane.doe@example.com"))
                .thenReturn(Optional.of(savedUser));
        when(userRepository.save(savedUser)).thenReturn(savedUser);
        when(modelMapper.map(savedUser, UserResponse.class)).thenReturn(expectedResponse);

        userService.updatePreferences("jane.doe@example.com", prefs);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getJobPreferences())
                .containsEntry("currency", "INR")
                .containsEntry("salaryMin", 100L)
                .containsEntry("salaryMax", 200L);

        verify(kafkaProducer).sendProfileUpdatedEvent(any(ProfileUpdatedEvent.class));
    }

    // ---------- updateRecruiterProfile ----------

    @Test
    @DisplayName("updateRecruiterProfile: persists company fields, publishes event with recruiter slice")
    void updateRecruiterProfile_success() {
        savedUser.setRole(User.Role.RECRUITER);
        RecruiterProfileRequest req = RecruiterProfileRequest.builder()
                .companyName("Acme Inc")
                .designation("Talent Lead")
                .companyWebsite("https://acme.example")
                .build();

        when(userRepository.findByEmailAndIsActiveTrue("rec@example.com"))
                .thenReturn(Optional.of(savedUser));
        when(userRepository.save(savedUser)).thenReturn(savedUser);
        when(modelMapper.map(savedUser, UserResponse.class)).thenReturn(expectedResponse);

        userService.updateRecruiterProfile("rec@example.com", req);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getCompanyName()).isEqualTo("Acme Inc");
        assertThat(userCaptor.getValue().getDesignation()).isEqualTo("Talent Lead");
        assertThat(userCaptor.getValue().getCompanyWebsite()).isEqualTo("https://acme.example");

        ArgumentCaptor<ProfileUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(ProfileUpdatedEvent.class);
        verify(kafkaProducer).sendProfileUpdatedEvent(eventCaptor.capture());
        ProfileUpdatedEvent event = eventCaptor.getValue();
        assertThat(event.getRole()).isEqualTo("RECRUITER");
        assertThat(event.getCompanyName()).isEqualTo("Acme Inc");
    }
}
