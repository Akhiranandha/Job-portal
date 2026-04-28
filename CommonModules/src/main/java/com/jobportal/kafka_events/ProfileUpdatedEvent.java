package com.jobportal.kafka_events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Published by User Service after every successful profile sub-resource update.
 * Carries a snapshot of the full current profile so consumers (e.g. Matching
 * Service in Phase 2) can replace their indexed view per email.
 *
 * Consumer-side dedupe key: (email, updatedAt).
 *
 * Candidate-only fields are null when role=RECRUITER, and vice versa.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdatedEvent {

    private String email;
    private String role;

    private List<String> skills;
    private List<Map<String, Object>> experience;
    private List<Map<String, Object>> education;
    private Map<String, Object> jobPreferences;

    private String companyName;
    private String designation;
    private String companyWebsite;

    private Instant updatedAt;
}
