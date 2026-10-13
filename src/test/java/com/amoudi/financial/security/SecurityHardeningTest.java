package com.amoudi.financial.security;

import com.amoudi.financial.dto.LoginRequest;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.User;
import com.amoudi.financial.model.UserPrincipal;
import com.amoudi.financial.repository.UserRepository;
import com.amoudi.financial.service.JwtService;
import com.amoudi.financial.utils.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private User financeUser;
    private User gmUser;
    private String financeToken;
    private String gmToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        financeUser = userRepository.save(User.builder()
                .username("finance_auditor")
                .passwordHash(passwordEncoder.encode("SecurePass123!"))
                .fullName("Finance Officer")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        gmUser = userRepository.save(User.builder()
                .username("gm_auditor")
                .passwordHash(passwordEncoder.encode("SecurePass123!"))
                .fullName("General Manager")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        financeToken = jwtService.generateToken(UserPrincipal.fromUser(financeUser));
        gmToken = jwtService.generateToken(UserPrincipal.fromUser(gmUser));
    }

    @Test
    @DisplayName("Expired JWT token is rejected with 401 Unauthorized")
    void expiredJwt_isRejectedWith401() throws Exception {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        String expiredToken = Jwts.builder()
                .claims(Map.of("role", "ROLE_FINANCE", "userId", financeUser.getId()))
                .subject(financeUser.getUsername())
                .issuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .signWith(Keys.hmacShaKeyFor(keyBytes))
                .compact();

        mockMvc.perform(get(Constants.AUTH_ME_ENDPOINT)
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.correlationId", notNullValue()));
    }

    @Test
    @DisplayName("Malformed/tampered JWT token is rejected with 401 Unauthorized")
    void malformedJwt_isRejectedWith401() throws Exception {
        mockMvc.perform(get(Constants.AUTH_ME_ENDPOINT)
                        .header("Authorization", "Bearer invalid.tampered.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("Missing Authorization token on protected endpoint is rejected with 401 Unauthorized")
    void missingToken_isRejectedWith401() throws Exception {
        mockMvc.perform(get(Constants.AUTH_ME_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("FINANCE role attempting GENERAL_MANAGER endpoint is rejected with 403 Forbidden")
    void financeRole_callingGmEndpoint_isForbiddenWith403() throws Exception {
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nonIataId\":1,\"amount\":1000.00,\"paymentDate\":\"2026-09-30\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("ACCESS_DENIED")))
                .andExpect(jsonPath("$.correlationId", notNullValue()));
    }

    @Test
    @DisplayName("GENERAL_MANAGER role attempting FINANCE-only cancel payment endpoint is rejected with 403 Forbidden")
    void gmRole_callingFinanceOnlyCancel_isForbiddenWith403() throws Exception {
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX + "/1/cancel")
                        .header("Authorization", "Bearer " + gmToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("ACCESS_DENIED")));
    }

    @Test
    @DisplayName("HTTP Security Headers are correctly present in all responses")
    void securityHeaders_arePresentInResponses() throws Exception {
        mockMvc.perform(get(Constants.HEALTH_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'self'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    @DisplayName("Custom X-Request-ID is echoed back in both response header and correlationId")
    void customRequestId_isEchoedBackInHeaders() throws Exception {
        String testRequestId = "req-audit-test-987654";
        mockMvc.perform(get(Constants.HEALTH_ENDPOINT)
                        .header("X-Request-ID", testRequestId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", testRequestId))
                .andExpect(header().string("X-Correlation-ID", testRequestId));
    }

    @Test
    @DisplayName("Authentication response never leaks password hash or internal credentials")
    void authResponse_neverExposesPasswordHash() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("finance_auditor")
                .password("SecurePass123!")
                .build();

        MvcResult result = mockMvc.perform(post(Constants.AUTH_LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertThat(content).doesNotContain("passwordHash");
        assertThat(content).doesNotContain("SecurePass123!");
        assertThat(content).doesNotContain("secret");
        assertThat(content).contains("accessToken");
        assertThat(content).contains("finance_auditor");
    }
}
