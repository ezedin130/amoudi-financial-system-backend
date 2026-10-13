package com.amoudi.financial.exception;

/**
 * Base unchecked exception for application-specific business errors.
 */
public class ApiException extends RuntimeException {

    private final String errorCode;

    public ApiException(String message) {
        super(message);
        this.errorCode = "API_ERROR";
    }

    public ApiException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
