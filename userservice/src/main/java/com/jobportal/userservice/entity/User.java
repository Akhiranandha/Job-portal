package com.jobportal.userservice.entity;

import com.jobportal.userservice.entity.converter.MapJsonConverter;
import com.jobportal.userservice.entity.converter.MapListJsonConverter;
import com.jobportal.userservice.entity.converter.StringListJsonConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Column(unique = true, nullable = false)
    private String email;

    @NotBlank(message = "First name is required")
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Past(message = "Date of birth must be in the past")
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 1000)
    private String address;

    private String city;

    private String state;

    private String country;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(length = 2000)
    private String bio;

    @NotNull(message = "Role is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_email_verified")
    private Boolean isEmailVerified = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "linked_in_url", length = 500)
    private String linkedInUrl;

    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    // Candidate-only profile fields. NULL for recruiters by convention.
    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "JSON")
    private List<String> skills;

    @Convert(converter = MapListJsonConverter.class)
    @Column(columnDefinition = "JSON")
    private List<Map<String, Object>> experience;

    @Convert(converter = MapListJsonConverter.class)
    @Column(columnDefinition = "JSON")
    private List<Map<String, Object>> education;

    @Convert(converter = MapJsonConverter.class)
    @Column(name = "job_preferences", columnDefinition = "JSON")
    private Map<String, Object> jobPreferences;

    // Recruiter-only profile fields. NULL for candidates by convention.
    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(length = 200)
    private String designation;

    @Column(name = "company_website", length = 500)
    private String companyWebsite;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Role {
        JOB_SEEKER,
        ADMIN,
        RECRUITER
    }
}
