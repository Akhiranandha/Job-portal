package com.jobportal.userservice.service;

import com.jobportal.userservice.dto.EducationEntry;
import com.jobportal.userservice.dto.ExperienceEntry;
import com.jobportal.userservice.dto.JobPreferencesDto;
import com.jobportal.userservice.dto.RecruiterProfileRequest;
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

    UserResponse updateSkills(String email, List<String> skills);

    UserResponse updateExperience(String email, List<ExperienceEntry> entries);

    UserResponse updateEducation(String email, List<EducationEntry> entries);

    UserResponse updatePreferences(String email, JobPreferencesDto preferences);

    UserResponse updateRecruiterProfile(String email, RecruiterProfileRequest request);
}
