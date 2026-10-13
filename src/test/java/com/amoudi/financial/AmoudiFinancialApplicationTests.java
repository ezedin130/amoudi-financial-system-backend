package com.amoudi.financial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AmoudiFinancialApplicationTests {

    @Test
    @DisplayName("Application context should start successfully with Flyway and Hibernate validation enabled")
    void contextLoads() {
        // If this test passes, it proves:
        // 1. Spring application context bootstraps cleanly.
        // 2. Flyway discovers and runs V1 migration.
        // 3. Hibernate ddl-auto=validate succeeds against the migrated database.
        // 4. Security configuration, filters, and repositories are correctly wired.
        assertTrue(true);
    }
}
