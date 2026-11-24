package com.jornal.catalog.enums;

import com.jornal.shared.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ErrorCatalog implements ErrorCode {

    CAT001_CATEGORY_NOTFOUND(HttpStatus.NOT_FOUND, "Category %s does not exist"),
    CAT002_SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "A category with slug '%s' already exists"),
    CAT003_LOCALE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "Locale not supported");

    private final HttpStatus status;
    private final String message;

    ErrorCatalog(HttpStatus status, String message){
        this.status = status;
        this.message = message;
    }

    @Override public HttpStatus status(){return status; }
    @Override public String message(){return message; }
}
