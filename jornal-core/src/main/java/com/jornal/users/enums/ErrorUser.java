package com.jornal.users.enums;

import com.jornal.shared.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ErrorUser implements ErrorCode {

    USER001_EMAIL_REGISTERED(HttpStatus.CONFLICT, "The email address is already registered"),
    USER002_USER_NOTFOUND(HttpStatus.NOT_FOUND, "User %s does not exist"),
    USER003_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Incorrect email or password"),
    USER004_ROLE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "This role cannot be selected at registration"),
    USER005_WRONG_PASSWORD(HttpStatus.BAD_REQUEST, "The current password is incorrect"),
    USER006_SAME_PASSWORD(HttpStatus.BAD_REQUEST, "The new password must be different from the current one"),
    USER007_COUNTRY_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "Country is not available"),
    USER008_INVALID_PHONE(HttpStatus.BAD_REQUEST, "Invalid phone number"),
    USER009_ADMIN_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "Administrator accounts cannot be deleted"),
    USER010_REGION_MISMATCH(HttpStatus.BAD_REQUEST, "This server only registers users from %s"),
    USER011_BUSINESS_REQUIRED(HttpStatus.BAD_REQUEST, "Business data is required for business accounts"),
    USER012_BUSINESS_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Business data is only allowed for business accounts"),
    USER013_TAX_ID_REGISTERED(HttpStatus.CONFLICT, "The tax identification is already registered"),
    USER014_ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "Address %s does not exist"),
    USER015_ADDRESS_LIMIT(HttpStatus.UNPROCESSABLE_ENTITY, "You cannot save more than %s addresses"),
    USER016_ADDRESS_IN_USE(HttpStatus.CONFLICT, "The address is used as billing address"),
    USER017_BUSINESS_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "The business profile does not exist"),
    AUTH001_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"),
    AUTH002_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts, try again later");

    private final HttpStatus status;
    private final String message;

    ErrorUser(HttpStatus status, String message){
        this.status = status;
        this.message = message;
    }
    @Override public HttpStatus status() { return status; }
    @Override public String message() { return message; }
}
