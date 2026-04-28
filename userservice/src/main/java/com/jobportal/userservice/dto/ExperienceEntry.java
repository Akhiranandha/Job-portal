package com.jobportal.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceEntry {

    @NotBlank(message = "Company is required")
    @Size(max = 200)
    private String company;

    @NotBlank(message = "Role is required")
    @Size(max = 200)
    private String role;

    @NotBlank(message = "Start date (YYYY-MM) is required")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "startDate must match YYYY-MM")
    private String startDate;

    // null indicates "current job"
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "endDate must match YYYY-MM")
    private String endDate;

    @Size(max = 2000)
    private String description;
}
