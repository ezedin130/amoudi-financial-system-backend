package com.amoudi.financial.controller;

import com.amoudi.financial.dto.HealthResponse;
import com.amoudi.financial.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check REST controller providing system readiness and liveness information.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * Endpoint: GET /api/v1/health
     * Returns application health status, database connectivity status, and environment.
     *
     * @return 200 OK with HealthResponse
     */
    @GetMapping
    public ResponseEntity<HealthResponse> getHealth() {
        HealthResponse response = healthService.getHealthStatus();
        return ResponseEntity.ok(response);
    }
}
