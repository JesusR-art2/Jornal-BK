package com.jornal.requests.enums;

import com.jornal.shared.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ErrorRequest implements ErrorCode {

    REQ001_NOT_FOUND(HttpStatus.NOT_FOUND, "Request %s does not exist"),
    REQ002_CATEGORY_UNAVAILABLE(HttpStatus.BAD_REQUEST, "The category is not available in this country"),
    REQ003_SCHEDULE_IN_PAST(HttpStatus.BAD_REQUEST, "The scheduled time must be in the future"),
    REQ004_PAYMENT_METHOD_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Payment method %s is not available in this country"),
    REQ005_TIP_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Tips are not used in this country"),
    REQ006_INVALID_TRANSITION(HttpStatus.CONFLICT, "The request cannot change from %s to %s"),
    REQ007_NOT_ALLOWED(HttpStatus.FORBIDDEN, "You cannot perform this action on the request"),
    REQ008_IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "The Idempotency-Key header is required"),
    REQ009_TIMEZONE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Time zone %s is not valid for this country"),
    REQ010_CLIENT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "Your account cannot create requests");

    private final HttpStatus status;
    private final String message;

    ErrorRequest(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String message() { return message; }
}
