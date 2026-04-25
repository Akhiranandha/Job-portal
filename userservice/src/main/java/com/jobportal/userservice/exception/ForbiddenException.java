package com.jobportal.userservice.exception;

public class ForbiddenException extends UserServiceException {
    public ForbiddenException(String message) {
        super(message);
    }
}
