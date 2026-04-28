package com.jobportal.authservice.service;

import com.jobportal.authservice.dto.LoginRequest;
import com.jobportal.authservice.dto.LoginResponse;
import com.jobportal.authservice.dto.PasswordUpdateRequest;
import com.jobportal.authservice.entity.User;
import com.jobportal.authservice.exception.AccountInactiveException;
import com.jobportal.authservice.exception.AuthenticationException;
import com.jobportal.authservice.exception.UserNotFoundException;
import com.jobportal.authservice.repository.UserRepository;
import com.jobportal.authservice.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthenticationException("Invalid credentials");
        }

        if (!user.isActive()) {
            throw new AccountInactiveException("Account is inactive. Please contact support.");
        }

        String token = jwtTokenProvider.generateToken(user);
        Instant expiresAt = jwtTokenProvider.getExpirationDate(token);

        return LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(user.getEmail())
                .email(user.getEmail())
                .role(user.getRole())
                .expiresAt(expiresAt)
                .build();
    }

    @Override
    @Transactional
    public void updatePassword(String email, PasswordUpdateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new AuthenticationException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
