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
public class RecruiterProfileRequest {

    @NotBlank(message = "companyName is required")
    @Size(max = 200, message = "companyName must be <= 200 chars")
    private String companyName;

    @Size(max = 200, message = "designation must be <= 200 chars")
    private String designation;

    @Size(max = 500, message = "companyWebsite must be <= 500 chars")
    @Pattern(
            regexp = "^$|^https?://[^\\s/$.?#].[^\\s]*$",
            message = "companyWebsite must be a valid http(s) URL"
    )
    private String companyWebsite;
}
