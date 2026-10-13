package com.amoudi.financial.exception;

/**
 * Thrown when client input or business precondition is invalid.
 * Maps to HTTP 400 BAD REQUEST.
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super("BAD_REQUEST", message);
    }

    public BadRequestException(String errorCode, String message) {
        super(errorCode, message);
    }
}
