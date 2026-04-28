package com.jobportal.userservice.service;

import com.jobportal.kafka_events.ProfileUpdatedEvent;
import com.jobportal.kafka_events.UserDeleteEvent;
import com.jobportal.kafka_events.UserRegistrationEvent;
import com.jobportal.userservice.dto.*;
import com.jobportal.userservice.entity.User;
import com.jobportal.userservice.exception.UserAlreadyExistsException;
import com.jobportal.userservice.exception.UserNotFoundException;
import com.jobportal.userservice.exception.UserServiceException;
import com.jobportal.userservice.kafka.KafkaProducer;
import com.jobportal.userservice.repository.UserRepository;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final KafkaProducer kafkaProducer;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                           KafkaProducer kafkaProducer,
                           ModelMapper modelMapper,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.kafkaProducer = kafkaProducer;
        this.modelMapper = modelMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UserResponse registerUser(UserRegistrationRequest request) {
        if (request.getRole() == User.Role.ADMIN) {
            throw new UserServiceException("ADMIN role cannot be self-assigned via public registration");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("User with email already exists: " + request.getEmail());
        }

        User user = modelMapper.map(request, User.class);
        user.setIsActive(true);
        user.setIsEmailVerified(false);

        User savedUser = userRepository.save(user);
        logger.info("User saved to database with email: {}", savedUser.getEmail());

        String passwordHash = passwordEncoder.encode(request.getPassword());

        UserRegistrationEvent credentialsEvent = UserRegistrationEvent.builder()
                .email(request.getEmail())
                .passwordHash(passwordHash)
                .role(request.getRole().name())
                .build();

        kafkaProducer.sendUserRegistrationEvent(credentialsEvent);
        logger.info("User credentials sent to auth service via Kafka for email: {}", request.getEmail());

        return modelMapper.map(savedUser, UserResponse.class);
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmailAndIsActiveTrue(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        return modelMapper.map(user, UserResponse.class);
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findByIsActiveTrue().stream()
                .map(user -> modelMapper.map(user, UserResponse.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserResponse updateUser(String email, UserUpdateRequest request) {
        User user = userRepository.findByEmailAndIsActiveTrue(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        modelMapper.map(request, user);

        User updatedUser = userRepository.save(user);
        logger.info("User updated with email: {}", updatedUser.getEmail());

        return modelMapper.map(updatedUser, UserResponse.class);
    }

    @Override
    @Transactional
    public void deleteUser(String email) {
        User user = userRepository.findByEmailAndIsActiveTrue(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        user.setIsActive(false);
        userRepository.save(user);
        logger.info("User deactivated (soft delete) with email: {}", email);

        UserDeleteEvent deleteEvent = new UserDeleteEvent(email);
        kafkaProducer.sendUserDeleteEvent(deleteEvent);
        logger.info("User delete event sent to auth service via Kafka for email: {}", email);
    }

    @Override
    @Transactional
    public UserResponse updateSkills(String email, List<String> skills) {
        User user = requireActiveUser(email);
        user.setSkills(skills == null ? null : new ArrayList<>(skills));
        return saveAndPublish(user);
    }

    @Override
    @Transactional
    public UserResponse updateExperience(String email, List<ExperienceEntry> entries) {
        User user = requireActiveUser(email);
        user.setExperience(toMapList(entries, this::experienceEntryToMap));
        return saveAndPublish(user);
    }

    @Override
    @Transactional
    public UserResponse updateEducation(String email, List<EducationEntry> entries) {
        User user = requireActiveUser(email);
        user.setEducation(toMapList(entries, this::educationEntryToMap));
        return saveAndPublish(user);
    }

    @Override
    @Transactional
    public UserResponse updatePreferences(String email, JobPreferencesDto preferences) {
        User user = requireActiveUser(email);
        user.setJobPreferences(preferences == null ? null : preferencesToMap(preferences));
        return saveAndPublish(user);
    }

    @Override
    @Transactional
    public UserResponse updateRecruiterProfile(String email, RecruiterProfileRequest request) {
        User user = requireActiveUser(email);
        user.setCompanyName(request.getCompanyName());
        user.setDesignation(request.getDesignation());
        user.setCompanyWebsite(request.getCompanyWebsite());
        return saveAndPublish(user);
    }

    private User requireActiveUser(String email) {
        return userRepository.findByEmailAndIsActiveTrue(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
    }

    private UserResponse saveAndPublish(User user) {
        User saved = userRepository.save(user);
        logger.info("Profile updated for email: {}", saved.getEmail());

        ProfileUpdatedEvent event = ProfileUpdatedEvent.builder()
                .email(saved.getEmail())
                .role(saved.getRole().name())
                .skills(saved.getSkills())
                .experience(saved.getExperience())
                .education(saved.getEducation())
                .jobPreferences(saved.getJobPreferences())
                .companyName(saved.getCompanyName())
                .designation(saved.getDesignation())
                .companyWebsite(saved.getCompanyWebsite())
                .updatedAt(saved.getUpdatedAt() == null
                        ? Instant.now()
                        : saved.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                .build();

        kafkaProducer.sendProfileUpdatedEvent(event);
        return modelMapper.map(saved, UserResponse.class);
    }

    private <T> List<Map<String, Object>> toMapList(List<T> entries, java.util.function.Function<T, Map<String, Object>> mapper) {
        if (entries == null) {
            return null;
        }
        return entries.stream().map(mapper).collect(Collectors.toList());
    }

    private Map<String, Object> experienceEntryToMap(ExperienceEntry e) {
        Map<String, Object> m = new HashMap<>();
        m.put("company", e.getCompany());
        m.put("role", e.getRole());
        m.put("startDate", e.getStartDate());
        m.put("endDate", e.getEndDate());
        m.put("description", e.getDescription());
        return m;
    }

    private Map<String, Object> educationEntryToMap(EducationEntry e) {
        Map<String, Object> m = new HashMap<>();
        m.put("institution", e.getInstitution());
        m.put("degree", e.getDegree());
        m.put("field", e.getField());
        m.put("startYear", e.getStartYear());
        m.put("endYear", e.getEndYear());
        return m;
    }

    private Map<String, Object> preferencesToMap(JobPreferencesDto p) {
        Map<String, Object> m = new HashMap<>();
        m.put("locations", p.getLocations());
        m.put("salaryMin", p.getSalaryMin());
        m.put("salaryMax", p.getSalaryMax());
        m.put("currency", p.getCurrency() == null ? "INR" : p.getCurrency());
        m.put("remote", p.getRemote());
        m.put("employmentTypes", p.getEmploymentTypes());
        return m;
    }
}
