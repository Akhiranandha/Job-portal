package com.jobportal.userservice.service;

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

import java.util.List;
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
}
