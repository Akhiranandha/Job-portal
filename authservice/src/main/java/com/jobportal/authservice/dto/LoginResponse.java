package com.jobportal.authservice.dto;

import com.jobportal.authservice.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    
    private String token;
    private String type;
    private String userId;
    private String email;
    private Role role;
    private Instant expiresAt;
}
