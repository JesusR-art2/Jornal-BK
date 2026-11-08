package com.jornal.Users.enums;

import com.jornal.Shared.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ErrorUser implements ErrorCode {

    USER001_EMAIL_REGISTERED(HttpStatus.CONFLICT, "The email address is already registered"),
    USER002_USER_NOTFOUND(HttpStatus.NOT_FOUND, "User %s does not exist"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Incorrect email or password");

    private final HttpStatus status;
    private final String message;

    ErrorUser(HttpStatus status, String message){
        this.status = status;
        this.message = message;
    }
    @Override public HttpStatus status() { return status; }
    @Override public String message() { return message; }
}
