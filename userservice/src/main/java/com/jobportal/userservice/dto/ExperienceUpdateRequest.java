package com.jobportal.userservice.dto;

import jakarta.validation.Valid;
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
public class ExperienceUpdateRequest {

    @NotNull(message = "experience array is required (use [] to clear)")
    @Size(max = 50, message = "At most 50 experience entries allowed")
    private List<@Valid ExperienceEntry> experience;
}
