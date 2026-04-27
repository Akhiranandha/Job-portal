package com.jobportal.userservice.service;

import com.jobportal.userservice.dto.UserRegistrationRequest;
import com.jobportal.userservice.dto.UserResponse;
import com.jobportal.userservice.dto.UserUpdateRequest;

import java.util.List;

public interface UserService {

    UserResponse registerUser(UserRegistrationRequest request);

    UserResponse getUserByEmail(String email);

    List<UserResponse> getAllUsers();

    UserResponse updateUser(String email, UserUpdateRequest request);

    void deleteUser(String email);
}
