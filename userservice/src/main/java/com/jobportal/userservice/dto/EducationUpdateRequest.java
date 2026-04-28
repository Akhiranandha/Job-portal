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
public class EducationUpdateRequest {

    @NotNull(message = "education array is required (use [] to clear)")
    @Size(max = 20, message = "At most 20 education entries allowed")
    private List<@Valid EducationEntry> education;
}
