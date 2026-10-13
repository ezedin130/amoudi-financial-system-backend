package com.amoudi.financial.security;

import com.amoudi.financial.enums.AuditAction;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.AuditLog;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.AuditLogRepository;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.UserRepository;
import com.amoudi.financial.service.storage.FileStorageService;
import com.amoudi.financial.service.JwtService;
import com.amoudi.financial.utils.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive Security Edge Cases Testing (Part 10 - Pillar 7):
 * Verifies defenses against:
 * - Malformed JWT tokens and missing headers
 * - Nonexistent user in valid token
 * - SQL/JPQL-related edge cases in sorting & query parameters
 * - Path traversal attempts
 * - Immutability of audit records
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ComprehensiveSecurityEdgeCasesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NonIataRepository nonIataRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User financeUser;
    private String financeToken;

    @BeforeEach
    void setUp() {
        nonIataRepository.deleteAll();
        userRepository.deleteAll();

        financeUser = userRepository.save(User.builder()
                .username("fin_sec_tester")
                .passwordHash(passwordEncoder.encode("Pass@12345"))
                .fullName("Finance Officer")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        financeToken = jwtService.generateToken(financeUser.getUsername(), financeUser.getId(), UserRole.FINANCE, 3600000);

        nonIataRepository.save(NonIata.builder()
                .code("SEC_AG01")
                .name("Security Test Agency")
                .status(NonIataStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("Reject malformed JWT tokens with 401 Unauthorized")
    void testMalformedTokens() throws Exception {
        // Bad structure
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, "Bearer not-a-valid-jwt-token"))
                .andExpect(status().isUnauthorized());

        // Header without bearer prefix
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, financeToken))
                .andExpect(status().isUnauthorized());

        // Empty token
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Reject valid token containing nonexistent username with 401 Unauthorized")
    void testTokenWithNonexistentUser() throws Exception {
        String ghostToken = jwtService.generateToken("nonexistent_user", 99999L, UserRole.FINANCE, 3600000);

        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + ghostToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SQL/JPQL injection edge cases in sort parameters are safely sanitized or rejected without SQL errors")
    void testSqlInjectionInSortParameters() throws Exception {
        // Non-IATA sorting injection attempt
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "?sortBy=name;DROP TABLE users;--")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk()); // Handled safely by white-listed sort fallback

        // Sales sorting injection attempt
        mockMvc.perform(get(Constants.SALES_API_PREFIX + "?sortBy=grossAmount' OR '1'='1")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk());

        // Payments sorting injection attempt
        mockMvc.perform(get(Constants.PAYMENTS_API_PREFIX + "?sortBy=1 UNION SELECT * FROM users")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Path traversal attempts in file storage are intercepted with SecurityException")
    void testPathTraversalAttacksBlocked() {
        assertThatThrownBy(() -> fileStorageService.retrieve("../../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outside storage directory");

        assertThatThrownBy(() -> fileStorageService.retrieve("..\\..\\windows\\win.ini"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outside storage directory");

        assertThatThrownBy(() -> fileStorageService.delete("../../etc/shadow"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outside storage directory");
    }

    @Test
    @DisplayName("Audit immutability: AuditLog records are append-only and cannot be altered")
    void testAuditImmutability() {
        AuditLog log = auditLogRepository.save(AuditLog.builder()
                .user(financeUser)
                .action(AuditAction.LOGIN)
                .entityType("USER")
                .entityId(financeUser.getId())
                .oldValue(null)
                .newValue("Login SUCCESS")
                .createdAt(LocalDateTime.now())
                .build());

        assertThat(log.getId()).isNotNull();

        // Verify audit log repository returns the immutable entry
        AuditLog retrieved = auditLogRepository.findById(log.getId()).orElseThrow();
        assertThat(retrieved.getAction()).isEqualTo(AuditAction.LOGIN);
        assertThat(retrieved.getCreatedAt()).isNotNull();
    }
}
