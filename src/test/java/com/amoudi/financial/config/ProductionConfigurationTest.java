package com.amoudi.financial.config;

import com.amoudi.financial.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionConfigurationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Environment environment;

    @Test
    @DisplayName("InitialUserSetup throws IllegalStateException in prod profile if default Admin@12345 is used")
    void initialUserSetup_failsWithDefaultPasswordInProd() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        InitialUserSetup setup = new InitialUserSetup(
                userRepository,
                passwordEncoder,
                environment,
                true,
                "admin",
                "Admin@12345", // default password
                "System Administrator",
                "GENERAL_MANAGER"
        );

        assertThatThrownBy(() -> setup.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Default insecure password 'Admin@12345' cannot be used in production profile");
    }

    @Test
    @DisplayName("InitialUserSetup succeeds in prod profile when explicit secure password is provided")
    void initialUserSetup_succeedsWithExplicitPasswordInProd() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("ProductionVaultKey987!")).thenReturn("hashed_prod_secret");

        InitialUserSetup setup = new InitialUserSetup(
                userRepository,
                passwordEncoder,
                environment,
                true,
                "admin",
                "ProductionVaultKey987!",
                "System Administrator",
                "GENERAL_MANAGER"
        );

        assertThatCode(() -> setup.run(null)).doesNotThrowAnyException();
        verify(userRepository).save(any());
    }

    @Test
    @DisplayName("InitialUserSetup succeeds with default password in dev profile for development convenience")
    void initialUserSetup_succeedsInDevProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Admin@12345")).thenReturn("hashed_dev_secret");

        InitialUserSetup setup = new InitialUserSetup(
                userRepository,
                passwordEncoder,
                environment,
                true,
                "admin",
                "Admin@12345",
                "System Administrator",
                "GENERAL_MANAGER"
        );

        assertThatCode(() -> setup.run(null)).doesNotThrowAnyException();
        verify(userRepository).save(any());
    }
}
