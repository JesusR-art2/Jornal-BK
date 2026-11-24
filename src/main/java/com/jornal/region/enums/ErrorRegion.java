package com.jornal.region.enums;

import com.jornal.shared.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ErrorRegion implements ErrorCode {

    REG001_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Region profile %s does not exist"),
    REG002_INVALID_ADDRESS(HttpStatus.BAD_REQUEST, "The address is not valid for this country"),
    REG003_DOCUMENT_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Document type %s is not accepted in this country"),
    REG004_INVALID_DOCUMENT(HttpStatus.BAD_REQUEST, "The document number is not valid"),
    REG005_VERSION_CONFLICT(HttpStatus.CONFLICT, "The region profile was modified by someone else, reload it"),
    REG006_INVALID_PROFILE(HttpStatus.BAD_REQUEST, "Invalid region profile: %s"),
    REG007_HOLIDAY_NOT_FOUND(HttpStatus.NOT_FOUND, "There is no holiday on %s");

    private final HttpStatus status;
    private final String message;

    ErrorRegion(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String message() { return message; }
}
