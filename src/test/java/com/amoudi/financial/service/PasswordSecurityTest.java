package com.amoudi.financial.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordSecurityTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("Should encode passwords using BCrypt format and salt")
    void shouldEncodePasswordsWithBCrypt() {
        String rawPassword = "SuperSecurePassword123!";
        String encoded = passwordEncoder.encode(rawPassword);

        // BCrypt hashes start with $2a$, $2b$, or $2y$ and are 60 chars long
        assertThat(encoded).startsWith("$2a$");
        assertThat(encoded).hasSize(60);
        assertThat(encoded).isNotEqualTo(rawPassword);

        // Encoding the same password twice produces different hashes due to random salt
        String encoded2 = passwordEncoder.encode(rawPassword);
        assertThat(encoded).isNotEqualTo(encoded2);

        // Both hashes match the raw password
        assertThat(passwordEncoder.matches(rawPassword, encoded)).isTrue();
        assertThat(passwordEncoder.matches(rawPassword, encoded2)).isTrue();

        // Incorrect password fails
        assertThat(passwordEncoder.matches("WrongPassword", encoded)).isFalse();
    }
}
