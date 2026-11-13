package com.jornal.Workers.enums;

import com.jornal.Shared.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ErrorWorker implements ErrorCode {

    WRK001_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Worker profile %s does not exist"),
    WRK002_NOT_A_WORKER(HttpStatus.FORBIDDEN, "Only active worker accounts can have a worker profile"),
    WRK003_INVALID_CATEGORIES(HttpStatus.BAD_REQUEST, "One or more categories do not exist or are not active"),
    WRK004_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Your account must be verified before you can be available"),
    WRK005_CATEGORY_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Select at least one active category before becoming available");

    private final HttpStatus status;
    private final String message;

    ErrorWorker(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String message() { return message; }
}
