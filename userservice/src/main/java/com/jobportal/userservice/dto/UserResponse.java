package com.jobportal.userservice.dto;

import com.jobportal.userservice.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String address;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String bio;
    private User.Role role;
    private Boolean isActive;
    private Boolean isEmailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String linkedInUrl;
    private String portfolioUrl;

    // Candidate-only fields (null for recruiters)
    private List<String> skills;
    private List<Map<String, Object>> experience;
    private List<Map<String, Object>> education;
    private Map<String, Object> jobPreferences;

    // Recruiter-only fields (null for candidates)
    private String companyName;
    private String designation;
    private String companyWebsite;
}
