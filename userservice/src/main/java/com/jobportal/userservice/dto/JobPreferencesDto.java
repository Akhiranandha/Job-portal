package com.jobportal.userservice.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPreferencesDto {

    private static final Set<String> ALLOWED_EMPLOYMENT_TYPES =
            Set.of("FULL_TIME", "PART_TIME", "CONTRACT", "INTERN");

    @Size(max = 50, message = "At most 50 locations allowed")
    private List<String> locations;

    @PositiveOrZero(message = "salaryMin must be >= 0")
    private Long salaryMin;

    @PositiveOrZero(message = "salaryMax must be >= 0")
    private Long salaryMax;

    // 3-letter ISO currency code; defaults to INR if null
    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
    private String currency;

    private Boolean remote;

    private List<String> employmentTypes;

    @AssertTrue(message = "salaryMin must be <= salaryMax")
    public boolean isSalaryRangeValid() {
        if (salaryMin == null || salaryMax == null) {
            return true;
        }
        return salaryMin <= salaryMax;
    }

    @AssertTrue(message = "employmentTypes must each be one of FULL_TIME, PART_TIME, CONTRACT, INTERN")
    public boolean isEmploymentTypesValid() {
        if (employmentTypes == null) {
            return true;
        }
        return employmentTypes.stream().allMatch(ALLOWED_EMPLOYMENT_TYPES::contains);
    }
}
