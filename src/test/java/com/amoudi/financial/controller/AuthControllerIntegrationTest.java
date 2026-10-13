package com.amoudi.financial.controller;

import com.amoudi.financial.dto.LoginRequest;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.UserRepository;
import com.amoudi.financial.service.JwtService;
import com.amoudi.financial.utils.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

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

    private User activeFinanceUser;
    private User activeGmUser;
    private User inactiveUser;
    private User lockedUser;

    private static final String RAW_PASSWORD = "CorrectPassword123!";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        activeFinanceUser = userRepository.save(User.builder()
                .username("finance_john")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName("John Finance")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        activeGmUser = userRepository.save(User.builder()
                .username("gm_sarah")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName("Sarah General Manager")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        inactiveUser = userRepository.save(User.builder()
                .username("inactive_bob")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName("Bob Inactive")
                .role(UserRole.FINANCE)
                .status(UserStatus.INACTIVE)
                .build());

        lockedUser = userRepository.save(User.builder()
                .username("locked_alice")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName("Alice Locked")
                .role(UserRole.FINANCE)
                .status(UserStatus.LOCKED)
                .build());
    }

    @Test
    @DisplayName("1. Active user can authenticate with correct password and receive valid JWT")
    void activeUserCanAuthenticateWithCorrectPassword() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("finance_john")
                .password(RAW_PASSWORD)
                .build();

        MvcResult result = mockMvc.perform(post(Constants.AUTH_LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.data.expiresIn", notNullValue()))
                .andExpect(jsonPath("$.data.user.id", is(activeFinanceUser.getId().intValue())))
                .andExpect(jsonPath("$.data.user.username", is("finance_john")))
                .andExpect(jsonPath("$.data.user.fullName", is("John Finance")))
                .andExpect(jsonPath("$.data.user.role", is("FINANCE")))
                .andExpect(jsonPath("$.data.user.status", is("ACTIVE")))
                // Never return password or passwordHash
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn();

        // Verify token is valid and subject is finance_john
        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        String token = jsonNode.path("data").path("accessToken").asText();

        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("finance_john");
    }

    @Test
    @DisplayName("2. Incorrect password is rejected with HTTP 401 and does not update last_login_at")
    void incorrectPasswordIsRejected() throws Exception {
        LocalDateTime initialLoginAt = activeFinanceUser.getLastLoginAt();

        LoginRequest loginRequest = LoginRequest.builder()
                .username("finance_john")
                .password("WrongPassword999!")
                .build();

        mockMvc.perform(post(Constants.AUTH_LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.message", containsString("Invalid username or password")));

        // Verify last_login_at was NOT modified in the database
        User refreshedUser = userRepository.findById(activeFinanceUser.getId()).orElseThrow();
        assertThat(refreshedUser.getLastLoginAt()).isEqualTo(initialLoginAt);
    }

    @Test
    @DisplayName("3. Unknown username is rejected with HTTP 401 without leaking user existence")
    void unknownUsernameIsRejected() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("non_existent_user_xyz")
                .password("SomePassword123!")
                .build();

        mockMvc.perform(post(Constants.AUTH_LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.message", containsString("Invalid username or password")));
    }

    @Test
    @DisplayName("4. INACTIVE user cannot authenticate")
    void inactiveUserCannotAuthenticate() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("inactive_bob")
                .password(RAW_PASSWORD)
                .build();

        mockMvc.perform(post(Constants.AUTH_LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.message", containsString("Account is inactive or locked")));
    }

    @Test
    @DisplayName("5. LOCKED user cannot authenticate")
    void lockedUserCannotAuthenticate() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("locked_alice")
                .password(RAW_PASSWORD)
                .build();

        mockMvc.perform(post(Constants.AUTH_LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.message", containsString("Account is inactive or locked")));
    }

    @Test
    @DisplayName("6. Successful login updates last_login_at")
    void successfulLoginUpdatesLastLoginAt() throws Exception {
        assertThat(activeFinanceUser.getLastLoginAt()).isNull();

        LoginRequest loginRequest = LoginRequest.builder()
                .username("finance_john")
                .password(RAW_PASSWORD)
                .build();

        mockMvc.perform(post(Constants.AUTH_LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());

        User refreshedUser = userRepository.findById(activeFinanceUser.getId()).orElseThrow();
        assertThat(refreshedUser.getLastLoginAt()).isNotNull();
        assertThat(refreshedUser.getLastLoginAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("7. GET /api/v1/auth/me returns current user profile with valid token")
    void meEndpointReturnsUserProfile() throws Exception {
        String token = jwtService.generateToken("finance_john", activeFinanceUser.getId(), UserRole.FINANCE, 3600000);

        mockMvc.perform(get(Constants.AUTH_ME_ENDPOINT)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(activeFinanceUser.getId().intValue())))
                .andExpect(jsonPath("$.data.username", is("finance_john")))
                .andExpect(jsonPath("$.data.fullName", is("John Finance")))
                .andExpect(jsonPath("$.data.role", is("FINANCE")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("8. GENERAL_MANAGER role is correctly represented and authenticated")
    void generalManagerCanAuthenticateAndAccessMe() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("gm_sarah")
                .password(RAW_PASSWORD)
                .build();

        MvcResult loginResult = mockMvc.perform(post(Constants.AUTH_LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role", is("GENERAL_MANAGER")))
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        mockMvc.perform(get(Constants.AUTH_ME_ENDPOINT)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role", is("GENERAL_MANAGER")))
                .andExpect(jsonPath("$.data.username", is("gm_sarah")));
    }

    @Test
    @DisplayName("9. Unauthenticated access to /api/v1/auth/me is rejected with HTTP 401")
    void unauthenticatedAccessToMeIsRejected() throws Exception {
        mockMvc.perform(get(Constants.AUTH_ME_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.message", containsString("Authentication is required")));
    }

    @Test
    @DisplayName("10. Access with invalid or tampered token is rejected with HTTP 401")
    void tamperedTokenIsRejected() throws Exception {
        String token = jwtService.generateToken("finance_john", activeFinanceUser.getId(), UserRole.FINANCE, 3600000);
        String tamperedToken = token.substring(0, token.length() - 6) + "xxxxxx";

        mockMvc.perform(get(Constants.AUTH_ME_ENDPOINT)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("11. Access with expired token is rejected with HTTP 401")
    void expiredTokenIsRejected() throws Exception {
        // Create already-expired token (-5000ms)
        String expiredToken = jwtService.generateToken("finance_john", activeFinanceUser.getId(), UserRole.FINANCE, -5000);

        mockMvc.perform(get(Constants.AUTH_ME_ENDPOINT)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("12. Public health endpoint remains accessible without authentication")
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get(Constants.HEALTH_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }
}
