package com.jornal.shared;

import lombok.Getter;

import java.util.Map;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode error;
    /** Argumentos del mensaje; se usan también para traducirlo con MessageSource. */
    private final Object[] args;
    /** Detalle opcional por campo (campo → código de error). */
    private final Map<String, String> fieldErrors;

    public ApiException(ErrorCode error, Object... args) {
        this(error, Map.of(), args);
    }

    private ApiException(ErrorCode error, Map<String, String> fieldErrors, Object... args) {
        super(args.length == 0 ? error.message() : error.message().formatted(args));
        this.error = error;
        this.args = args;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public static ApiException withFields(ErrorCode error, Map<String, String> fieldErrors) {
        return new ApiException(error, fieldErrors);
    }
}
