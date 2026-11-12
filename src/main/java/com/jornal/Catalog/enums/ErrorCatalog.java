package com.jornal.Catalog.enums;

import com.jornal.Shared.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ErrorCatalog implements ErrorCode {

    CAT001_CATEGORY_NOTFOUND(HttpStatus.NOT_FOUND, "Category %s does exist"),
    CAT002_SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "A category with slig '%s' already exists");

    private final HttpStatus status;
    private final String message;

    ErrorCatalog(HttpStatus status, String message){
        this.status = status;
        this.message = message;
    }

    @Override public HttpStatus status(){return status; }
    @Override public String message(){return message; }
}
