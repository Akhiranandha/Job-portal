package com.jobportal.userservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationEntry {

    @NotBlank(message = "Institution is required")
    @Size(max = 200)
    private String institution;

    @NotBlank(message = "Degree is required")
    @Size(max = 200)
    private String degree;

    @NotBlank(message = "Field is required")
    @Size(max = 200)
    private String field;

    @NotNull(message = "startYear is required")
    @Min(value = 1900, message = "startYear must be >= 1900")
    @Max(value = 9999, message = "startYear must be <= 9999")
    private Integer startYear;

    // null indicates "currently enrolled"
    @Min(value = 1900, message = "endYear must be >= 1900")
    @Max(value = 9999, message = "endYear must be <= 9999")
    private Integer endYear;
}
