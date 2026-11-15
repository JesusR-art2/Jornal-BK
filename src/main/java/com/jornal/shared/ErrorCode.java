package com.jornal.shared;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    String name();
    HttpStatus status();
    String message();
}
