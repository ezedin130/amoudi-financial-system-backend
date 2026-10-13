package com.amoudi.financial.service;

import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.UserPrincipal;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    private JwtService jwtService;
    private UserPrincipal testPrincipal;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, EXPIRATION_MS);

        testPrincipal = UserPrincipal.builder()
                .id(100L)
                .username("test_finance")
                .password("$2a$10$hashedpassword")
                .fullName("Finance Officer")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_FINANCE")))
                .build();
    }

    @Test
    @DisplayName("Should generate a valid signed JWT containing username, userId, and role")
    void shouldGenerateValidToken() {
        String token = jwtService.generateToken(testPrincipal);

        assertThat(token).isNotBlank();
        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("test_finance");
        assertThat(jwtService.extractUserId(token)).isEqualTo(100L);
        assertThat(jwtService.extractRole(token)).isEqualTo("FINANCE");
        assertThat(jwtService.isTokenValid(token, testPrincipal)).isTrue();
    }

    @Test
    @DisplayName("Should reject token when signature is tampered with")
    void shouldRejectTamperedToken() {
        String token = jwtService.generateToken(testPrincipal);

        // Tamper signature by modifying the last characters
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        assertThat(jwtService.validateToken(tamperedToken)).isFalse();
        assertThat(jwtService.isTokenValid(tamperedToken, testPrincipal)).isFalse();
    }

    @Test
    @DisplayName("Should detect expired token")
    void shouldDetectExpiredToken() {
        // Generate token with negative duration (already expired)
        String expiredToken = jwtService.generateToken("test_finance", 100L, UserRole.FINANCE, -10000);

        assertThat(jwtService.isTokenExpired(expiredToken)).isTrue();
        assertThat(jwtService.validateToken(expiredToken)).isFalse();
        assertThat(jwtService.isTokenValid(expiredToken, testPrincipal)).isFalse();
    }

    @Test
    @DisplayName("Should reject token for different user")
    void shouldRejectTokenForDifferentUser() {
        String token = jwtService.generateToken(testPrincipal);

        UserPrincipal otherPrincipal = UserPrincipal.builder()
                .id(200L)
                .username("other_user")
                .password("$2a$10$hashedpassword")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build();

        assertThat(jwtService.isTokenValid(token, otherPrincipal)).isFalse();
    }

    @Test
    @DisplayName("Should extract expiration timestamp correctly")
    void shouldExtractExpirationTimestamp() {
        long now = System.currentTimeMillis();
        String token = jwtService.generateToken(testPrincipal);

        Date expiration = jwtService.extractExpiration(token);

        // JWT timestamps are stored in epoch seconds; verify within 2-second tolerance
        assertThat(Math.abs(expiration.getTime() - (now + EXPIRATION_MS))).isLessThanOrEqualTo(2000L);
    }

    @Test
    @DisplayName("Should throw exception if secret key is less than 256 bits")
    void shouldThrowIfSecretTooShort() {
        assertThatThrownBy(() -> new JwtService("short-secret-less-than-32-bytes", EXPIRATION_MS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 256 bits");
    }
}
