package com.amoudi.financial.config;

import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Safe bootstrapping runner to initialize a default administrator account
 * if no user account exists for the configured administrative username.
 *
 * Configurable via environment variables:
 * - INITIAL_USER_ENABLED (default: true)
 * - INITIAL_USER_USERNAME (default: admin)
 * - INITIAL_USER_PASSWORD (default: Admin@12345 in dev/test; MUST be set via env in production)
 * - INITIAL_USER_FULL_NAME (default: System Administrator)
 * - INITIAL_USER_ROLE (default: GENERAL_MANAGER)
 */
@Slf4j
@Component
public class InitialUserSetup implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String initialUsername;
    private final String initialPassword;
    private final String initialFullName;
    private final String initialRole;

    private final org.springframework.core.env.Environment environment;

    public InitialUserSetup(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            org.springframework.core.env.Environment environment,
            @Value("${app.security.initial-user.enabled:true}") boolean enabled,
            @Value("${app.security.initial-user.username:admin}") String initialUsername,
            @Value("${app.security.initial-user.password:Admin@12345}") String initialPassword,
            @Value("${app.security.initial-user.full-name:System Administrator}") String initialFullName,
            @Value("${app.security.initial-user.role:GENERAL_MANAGER}") String initialRole
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.enabled = enabled;
        this.initialUsername = initialUsername;
        this.initialPassword = initialPassword;
        this.initialFullName = initialFullName;
        this.initialRole = initialRole;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("Initial user creation is disabled via configuration");
            return;
        }

        // Production safety check: prevent hardcoded default password
        boolean isProd = java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProd && "Admin@12345".equals(initialPassword)) {
            throw new IllegalStateException(
                    "CRITICAL: Default insecure password 'Admin@12345' cannot be used in production profile. " +
                    "Set INITIAL_USER_PASSWORD environment variable or disable INITIAL_USER_ENABLED.");
        }

        if (userRepository.findByUsername(initialUsername).isPresent()) {
            log.debug("User '{}' already exists. Initial user provisioning skipped.", initialUsername);
            return;
        }

        UserRole role;
        try {
            role = UserRole.valueOf(initialRole.toUpperCase());
        } catch (IllegalArgumentException e) {
            role = UserRole.GENERAL_MANAGER;
        }

        User initialUser = User.builder()
                .username(initialUsername)
                .passwordHash(passwordEncoder.encode(initialPassword))
                .fullName(initialFullName)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(initialUser);
        log.info("Initialized default administrative user '{}' with role '{}' (Status: ACTIVE)",
                initialUsername, role);
    }
}
