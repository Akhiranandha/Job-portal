package com.jobportal.authservice.service;

import com.jobportal.authservice.dto.LoginRequest;
import com.jobportal.authservice.dto.LoginResponse;
import com.jobportal.authservice.dto.PasswordUpdateRequest;

public interface AuthService {
    
    LoginResponse login(LoginRequest request);
    
    void updatePassword(PasswordUpdateRequest request);
}
