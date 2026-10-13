package com.amoudi.financial.exception;

/**
 * Thrown when an expected resource cannot be found.
 * Maps to HTTP 404 NOT FOUND.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message);
    }

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super("RESOURCE_NOT_FOUND", String.format("%s with identifier '%s' was not found", resourceName, identifier));
    }
}
