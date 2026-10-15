package com.amoudi.financial.controller;

import com.amoudi.financial.dto.ForgotPasswordRequest;
import com.amoudi.financial.dto.LoginRequest;
import com.amoudi.financial.dto.ResetPasswordRequest;
import com.amoudi.financial.dto.VerifyResetOtpRequest;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.UserRepository;
import com.amoudi.financial.service.EmailService;
import com.amoudi.financial.utils.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PasswordRecoveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmailService emailService;

    private User testUser;
    private static final String INITIAL_PASSWORD = "InitialPassword123!";
    private static final String NEW_PASSWORD = "NewPassword2026!#";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .username("finance_recovery_tester")
                .passwordHash(passwordEncoder.encode(INITIAL_PASSWORD))
                .fullName("Recovery Tester")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("1. Forgot password returns HTTP 200 with generic anti-enumeration response for existing user")
    void forgotPasswordReturnsGenericResponseForExistingUser() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("finance_recovery_tester");

        mockMvc.perform(post(Constants.AUTH_FORGOT_PASSWORD_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("If an active account exists")));

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordRecoveryOtp(eq("finance_recovery_tester"), otpCaptor.capture(), eq(5));
        assertThat(otpCaptor.getValue()).matches("^[0-9]{6}$");
    }

    @Test
    @DisplayName("2. Forgot password returns identical HTTP 200 generic message for unknown user without sending email")
    void forgotPasswordReturnsGenericResponseForUnknownUser() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("completely_unknown_user");

        mockMvc.perform(post(Constants.AUTH_FORGOT_PASSWORD_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("If an active account exists")));

        verify(emailService, never()).sendPasswordRecoveryOtp(eq("completely_unknown_user"), org.mockito.ArgumentMatchers.anyString(), anyInt());
    }

    @Test
    @DisplayName("3. Forgot password returns HTTP 400 for blank username")
    void forgotPasswordRejectsBlankUsername() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("");

        mockMvc.perform(post(Constants.AUTH_FORGOT_PASSWORD_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("4. Verify OTP rejects non-6-digit OTP format with HTTP 400")
    void verifyOtpRejectsMalformedOtp() throws Exception {
        VerifyResetOtpRequest request = new VerifyResetOtpRequest("finance_recovery_tester", "1234");

        mockMvc.perform(post(Constants.AUTH_VERIFY_RESET_OTP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("5. Verify OTP rejects incorrect code with HTTP 400 and returns attempts remaining")
    void verifyOtpRejectsIncorrectCode() throws Exception {
        // First, request OTP
        mockMvc.perform(post(Constants.AUTH_FORGOT_PASSWORD_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("finance_recovery_tester"))))
                .andExpect(status().isOk());

        // Attempt verification with wrong OTP
        VerifyResetOtpRequest verifyRequest = new VerifyResetOtpRequest("finance_recovery_tester", "000000");

        mockMvc.perform(post(Constants.AUTH_VERIFY_RESET_OTP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Attempts remaining: 4")));
    }

    @Test
    @DisplayName("6. End-to-end recovery: Request OTP -> Verify OTP -> Reset Password -> Login with new password")
    void completeEndToEndPasswordRecoveryFlow() throws Exception {
        // Step 1: Request OTP
        ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest("finance_recovery_tester");
        mockMvc.perform(post(Constants.AUTH_FORGOT_PASSWORD_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotRequest)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordRecoveryOtp(eq("finance_recovery_tester"), otpCaptor.capture(), eq(5));
        String generatedOtp = otpCaptor.getValue();
        assertThat(generatedOtp).matches("^[0-9]{6}$");

        // Step 2: Verify OTP
        VerifyResetOtpRequest verifyRequest = new VerifyResetOtpRequest("finance_recovery_tester", generatedOtp);
        MvcResult verifyResult = mockMvc.perform(post(Constants.AUTH_VERIFY_RESET_OTP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.resetToken", notNullValue()))
                .andReturn();

        JsonNode verifyNode = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
        String resetToken = verifyNode.path("data").path("resetToken").asText();
        assertThat(resetToken).isNotBlank();

        // Step 3: Attempt reset with mismatching passwords (should fail)
        ResetPasswordRequest mismatchRequest = new ResetPasswordRequest(resetToken, NEW_PASSWORD, "DifferentPassword123!");
        mockMvc.perform(post(Constants.AUTH_RESET_PASSWORD_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mismatchRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("PASSWORD_MISMATCH")));

        // Step 4: Reset password with matching valid new password
        ResetPasswordRequest validResetRequest = new ResetPasswordRequest(resetToken, NEW_PASSWORD, NEW_PASSWORD);
        mockMvc.perform(post(Constants.AUTH_RESET_PASSWORD_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validResetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("successfully reset")));

        // Step 5: Verify reset token cannot be reused (single-use enforcement)
        mockMvc.perform(post(Constants.AUTH_RESET_PASSWORD_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validResetRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("INVALID_RESET_TOKEN")));

        // Step 6: Authenticate using the OLD password -> should be rejected with HTTP 401
        LoginRequest oldLoginRequest = new LoginRequest("finance_recovery_tester", INITIAL_PASSWORD);
        mockMvc.perform(post(Constants.AUTH_LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLoginRequest)))
                .andExpect(status().isUnauthorized());

        // Step 7: Authenticate using the NEW password -> should succeed with HTTP 200 and return JWT
        LoginRequest newLoginRequest = new LoginRequest("finance_recovery_tester", NEW_PASSWORD);
        mockMvc.perform(post(Constants.AUTH_LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.username", is("finance_recovery_tester")));
    }
}
