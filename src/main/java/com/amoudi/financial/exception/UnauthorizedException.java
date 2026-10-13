package com.amoudi.financial.exception;

/**
 * Exception thrown when authentication fails or credentials are invalid.
 * Maps to HTTP 401 Unauthorized.
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super("UNAUTHORIZED", message, cause);
    }
}
