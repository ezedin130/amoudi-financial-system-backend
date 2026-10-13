package com.amoudi.financial.dto;

import java.time.Instant;

/**
 * Health check response DTO confirming the backend status, database readiness, and environment.
 */
public record HealthResponse(
        String status,
        String application,
        String timestamp,
        String database,
        String environment
) {
    public static HealthResponse up(String application, String database, String environment) {
        return new HealthResponse(
                "UP",
                application,
                Instant.now().toString(),
                database,
                environment
        );
    }
}
