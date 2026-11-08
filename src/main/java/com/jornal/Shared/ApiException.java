package com.jornal.Shared;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode error;


    // Para mensajes con datos variables
    public ApiException (ErrorCode error, Object... args){
        super(args.length == 0 ? error.message() : error.message().formatted(args));
        this.error = error;
    }
}
