package com.jobportal.userservice.dto;

import com.jobportal.userservice.exception.ErrorCode;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        boolean success,
        String message,
        String error,
        Map<String, String> fieldErrors,
        Instant timestamp
) {
    public static ErrorResponse of(String message, ErrorCode code) {
        return new ErrorResponse(false, message, code.name(), null, Instant.now());
    }

    public static ErrorResponse validation(String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(false, message, ErrorCode.VALIDATION_FAILED.name(), fieldErrors, Instant.now());
    }
}
