package com.amoudi.financial.service;

import com.amoudi.financial.dto.HealthResponse;
import com.amoudi.financial.repository.SystemStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

/**
 * Default implementation of HealthService.
 * Validates database connectivity by verifying the baseline migration status record.
 */
@Service
public class HealthServiceImpl implements HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthServiceImpl.class);
    private static final String APP_NAME = "Amoudi Financial Management";
    private static final String INITIAL_STATUS_KEY = "DATABASE_INITIALIZED";

    private final SystemStatusRepository systemStatusRepository;
    private final Environment environment;

    public HealthServiceImpl(SystemStatusRepository systemStatusRepository, Environment environment) {
        this.systemStatusRepository = systemStatusRepository;
        this.environment = environment;
    }

    @Override
    @Transactional(readOnly = true)
    public HealthResponse getHealthStatus() {
        String dbStatus = checkDatabaseConnectivity();
        String activeProfile = getActiveProfile();

        log.debug("Health check executed: dbStatus={}, profile={}", dbStatus, activeProfile);
        return HealthResponse.up(APP_NAME, dbStatus, activeProfile);
    }

    private String checkDatabaseConnectivity() {
        try {
            boolean initialized = systemStatusRepository.findByStatusName(INITIAL_STATUS_KEY).isPresent();
            return initialized ? "CONNECTED" : "CONNECTED_UNINITIALIZED";
        } catch (Exception ex) {
            log.warn("Database connectivity check failed during health check: {}", ex.getMessage());
            return "UNAVAILABLE";
        }
    }

    private String getActiveProfile() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        return profiles.length > 0 ? Arrays.toString(profiles) : "default";
    }
}
