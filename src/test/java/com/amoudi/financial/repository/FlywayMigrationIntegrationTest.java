package com.amoudi.financial.repository;

import com.amoudi.financial.model.SystemStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIntegrationTest {

    @Autowired
    private SystemStatusRepository systemStatusRepository;

    @Test
    @DisplayName("Flyway V1 migration should successfully execute and seed the initial system status record")
    void flywayMigrationShouldSeedInitialRecord() {
        Optional<SystemStatus> status = systemStatusRepository.findByStatusName("DATABASE_INITIALIZED");

        assertThat(status).isPresent();
        assertThat(status.get().getStatusName()).isEqualTo("DATABASE_INITIALIZED");
        assertThat(status.get().getStatusValue()).isEqualTo("SUCCESS");
        assertThat(status.get().getCreatedAt()).isNotNull();
        assertThat(status.get().getUpdatedAt()).isNotNull();
    }
}
