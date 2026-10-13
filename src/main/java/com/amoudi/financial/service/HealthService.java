package com.amoudi.financial.service;

import com.amoudi.financial.dto.HealthResponse;

/**
 * Service providing application runtime and database connectivity health status.
 */
public interface HealthService {

    /**
     * Inspects application readiness and database connectivity.
     *
     * @return current HealthResponse details
     */
    HealthResponse getHealthStatus();
}
