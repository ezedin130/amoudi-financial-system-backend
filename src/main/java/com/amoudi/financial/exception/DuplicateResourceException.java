package com.amoudi.financial.exception;

/**
 * Thrown when a resource creation or update conflicts with existing data (e.g. duplicate code).
 * Maps to HTTP 409 CONFLICT.
 */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String message) {
        super("DUPLICATE_RESOURCE", message);
    }

    public DuplicateResourceException(String resourceName, String fieldName, Object value) {
        super("DUPLICATE_RESOURCE", String.format("%s with %s '%s' already exists", resourceName, fieldName, value));
    }
}
