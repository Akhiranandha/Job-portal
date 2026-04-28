package com.jobportal.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillsUpdateRequest {

    @NotNull(message = "skills array is required (use [] to clear)")
    @Size(max = 50, message = "At most 50 skills allowed")
    private List<@NotBlank(message = "skill must not be blank") @Size(max = 100, message = "skill must be <= 100 chars") String> skills;
}
