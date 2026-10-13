package com.amoudi.financial.exception;

/**
 * Thrown when an uploaded file fails format, structural, or header validation.
 * Maps to HTTP 400 BAD REQUEST.
 */
public class FileValidationException extends BadRequestException {

    public FileValidationException(String message) {
        super("FILE_VALIDATION_ERROR", message);
    }
}
