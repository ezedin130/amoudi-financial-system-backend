package com.amoudi.financial.service;

import com.amoudi.financial.dto.ForgotPasswordRequest;
import com.amoudi.financial.dto.ForgotPasswordResponse;
import com.amoudi.financial.dto.ResetPasswordRequest;
import com.amoudi.financial.dto.VerifyResetOtpRequest;
import com.amoudi.financial.dto.VerifyResetOtpResponse;
import com.amoudi.financial.enums.AuditAction;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.exception.BadRequestException;
import com.amoudi.financial.model.AuditLog;
import com.amoudi.financial.model.PasswordResetToken;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.AuditLogRepository;
import com.amoudi.financial.repository.PasswordResetTokenRepository;
import com.amoudi.financial.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {

    private static final String SECRET_PEPPER = "test-pepper-secret-404E635266556A586E3272357538782F413F4428472B4B62";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private AuditLogRepository auditLogRepository;

    private PasswordEncoder passwordEncoder;
    private PasswordRecoveryServiceImpl recoveryService;

    @Captor
    private ArgumentCaptor<PasswordResetToken> tokenCaptor;

    @Captor
    private ArgumentCaptor<AuditLog> auditCaptor;

    private User activeUser;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        recoveryService = new PasswordRecoveryServiceImpl(
                userRepository,
                tokenRepository,
                emailService,
                passwordEncoder,
                auditLogRepository,
                SECRET_PEPPER
        );

        activeUser = User.builder()
                .username("finance_user")
                .passwordHash(passwordEncoder.encode("OldPassword123!"))
                .fullName("Finance Officer")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build();
        activeUser.setId(10L);
    }

    private String calculateExpectedOtpHash(String username, String otp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET_PEPPER.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal((username.toLowerCase() + ":" + otp).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private String calculateSha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
    }

    @Nested
    @DisplayName("Request OTP (Forgot Password)")
    class RequestOtpTests {

        @Test
        @DisplayName("Returns generic message and does not send email if username does not exist")
        void shouldHandleNonExistentUserWithGenericResponse() {
            when(userRepository.findByUsername("unknown_user")).thenReturn(Optional.empty());

            ForgotPasswordResponse response = recoveryService.requestOtp(
                    new ForgotPasswordRequest("unknown_user")
            );

            assertThat(response.getMessage()).isEqualTo(PasswordRecoveryServiceImpl.GENERIC_REQUEST_RESPONSE);
            verify(emailService, never()).sendPasswordRecoveryOtp(anyString(), anyString(), anyInt());
            verify(tokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("Returns generic message and does not send email if user is not ACTIVE")
        void shouldHandleInactiveUserWithGenericResponse() {
            activeUser.setStatus(UserStatus.LOCKED);
            when(userRepository.findByUsername("finance_user")).thenReturn(Optional.of(activeUser));

            ForgotPasswordResponse response = recoveryService.requestOtp(
                    new ForgotPasswordRequest("finance_user")
            );

            assertThat(response.getMessage()).isEqualTo(PasswordRecoveryServiceImpl.GENERIC_REQUEST_RESPONSE);
            verify(emailService, never()).sendPasswordRecoveryOtp(anyString(), anyString(), anyInt());
            verify(tokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("Generates 6-digit OTP, saves digest, and sends email to shared inbox for active user")
        void shouldGenerateOtpAndSendEmailForActiveUser() {
            when(userRepository.findByUsername("finance_user")).thenReturn(Optional.of(activeUser));
            when(tokenRepository.findTopByUserAndConsumedAtIsNullOrderByCreatedAtDesc(activeUser))
                    .thenReturn(Optional.empty());
            when(tokenRepository.findByUserAndConsumedAtIsNull(activeUser))
                    .thenReturn(Collections.emptyList());

            ForgotPasswordResponse response = recoveryService.requestOtp(
                    new ForgotPasswordRequest("finance_user")
            );

            assertThat(response.getMessage()).isEqualTo(PasswordRecoveryServiceImpl.GENERIC_REQUEST_RESPONSE);

            verify(tokenRepository).save(tokenCaptor.capture());
            PasswordResetToken saved = tokenCaptor.getValue();
            assertThat(saved.getUser()).isEqualTo(activeUser);
            assertThat(saved.getOtpHash()).isNotEmpty();
            assertThat(saved.getAttemptCount()).isZero();
            assertThat(saved.getMaxAttempts()).isEqualTo(5);
            assertThat(saved.getOtpExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(4));

            ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendPasswordRecoveryOtp(eq("finance_user"), otpCaptor.capture(), eq(5));
            String dispatchedOtp = otpCaptor.getValue();
            assertThat(dispatchedOtp).matches("^[0-9]{6}$");
        }

        @Test
        @DisplayName("Enforces 30-second rate limit between OTP generation requests")
        void shouldEnforceRateLimitOnFrequentOtpRequests() {
            PasswordResetToken recentToken = PasswordResetToken.builder()
                    .user(activeUser)
                    .otpHash("hash")
                    .otpExpiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();
            recentToken.setCreatedAt(LocalDateTime.now().minusSeconds(10));

            when(userRepository.findByUsername("finance_user")).thenReturn(Optional.of(activeUser));
            when(tokenRepository.findTopByUserAndConsumedAtIsNullOrderByCreatedAtDesc(activeUser))
                    .thenReturn(Optional.of(recentToken));

            assertThatThrownBy(() -> recoveryService.requestOtp(new ForgotPasswordRequest("finance_user")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("wait 30 seconds");

            verify(emailService, never()).sendPasswordRecoveryOtp(anyString(), anyString(), anyInt());
        }

        @Test
        @DisplayName("Invalidates prior unconsumed tokens when generating a new OTP")
        void shouldInvalidatePriorUnconsumedTokens() {
            PasswordResetToken oldToken = PasswordResetToken.builder()
                    .user(activeUser)
                    .otpHash("old_hash")
                    .otpExpiresAt(LocalDateTime.now().plusMinutes(3))
                    .build();
            oldToken.setCreatedAt(LocalDateTime.now().minusSeconds(45));

            when(userRepository.findByUsername("finance_user")).thenReturn(Optional.of(activeUser));
            when(tokenRepository.findTopByUserAndConsumedAtIsNullOrderByCreatedAtDesc(activeUser))
                    .thenReturn(Optional.of(oldToken));
            when(tokenRepository.findByUserAndConsumedAtIsNull(activeUser))
                    .thenReturn(List.of(oldToken));

            recoveryService.requestOtp(new ForgotPasswordRequest("finance_user"));

            assertThat(oldToken.getConsumedAt()).isNotNull();
            verify(tokenRepository).saveAll(List.of(oldToken));
        }
    }

    @Nested
    @DisplayName("Verify OTP")
    class VerifyOtpTests {

        @Test
        @DisplayName("Rejects verification when username does not exist")
        void shouldRejectWhenUserNotFound() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recoveryService.verifyOtp(new VerifyResetOtpRequest("ghost", "123456")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("invalid or has expired");
        }

        @Test
        @DisplayName("Rejects verification when OTP has expired and marks token consumed")
        void shouldRejectWhenOtpExpired() {
            PasswordResetToken expiredToken = PasswordResetToken.builder()
                    .user(activeUser)
                    .otpHash("dummy_hash")
                    .otpExpiresAt(LocalDateTime.now().minusMinutes(1))
                    .build();

            when(userRepository.findByUsername("finance_user")).thenReturn(Optional.of(activeUser));
            when(tokenRepository.findTopByUserAndConsumedAtIsNullOrderByCreatedAtDesc(activeUser))
                    .thenReturn(Optional.of(expiredToken));

            assertThatThrownBy(() -> recoveryService.verifyOtp(new VerifyResetOtpRequest("finance_user", "123456")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("expired");

            assertThat(expiredToken.isConsumed()).isTrue();
            verify(tokenRepository).save(expiredToken);
        }

        @Test
        @DisplayName("Increments attempt count on incorrect OTP and reports remaining attempts")
        void shouldIncrementAttemptCountOnWrongOtp() throws Exception {
            String correctHash = calculateExpectedOtpHash("finance_user", "654321");
            PasswordResetToken token = PasswordResetToken.builder()
                    .user(activeUser)
                    .otpHash(correctHash)
                    .attemptCount(0)
                    .maxAttempts(5)
                    .otpExpiresAt(LocalDateTime.now().plusMinutes(4))
                    .build();

            when(userRepository.findByUsername("finance_user")).thenReturn(Optional.of(activeUser));
            when(tokenRepository.findTopByUserAndConsumedAtIsNullOrderByCreatedAtDesc(activeUser))
                    .thenReturn(Optional.of(token));

            assertThatThrownBy(() -> recoveryService.verifyOtp(new VerifyResetOtpRequest("finance_user", "000000")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Attempts remaining: 4");

            assertThat(token.getAttemptCount()).isEqualTo(1);
            verify(tokenRepository).save(token);
        }

        @Test
        @DisplayName("Locks and invalidates token when attempt count reaches 5")
        void shouldLockTokenAfterMaxAttempts() throws Exception {
            String correctHash = calculateExpectedOtpHash("finance_user", "654321");
            PasswordResetToken token = PasswordResetToken.builder()
                    .user(activeUser)
                    .otpHash(correctHash)
                    .attemptCount(4)
                    .maxAttempts(5)
                    .otpExpiresAt(LocalDateTime.now().plusMinutes(4))
                    .build();

            when(userRepository.findByUsername("finance_user")).thenReturn(Optional.of(activeUser));
            when(tokenRepository.findTopByUserAndConsumedAtIsNullOrderByCreatedAtDesc(activeUser))
                    .thenReturn(Optional.of(token));

            assertThatThrownBy(() -> recoveryService.verifyOtp(new VerifyResetOtpRequest("finance_user", "111111")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Maximum verification attempts exceeded");

            assertThat(token.getAttemptCount()).isEqualTo(5);
            assertThat(token.isConsumed()).isTrue();
            verify(tokenRepository).save(token);
        }

        @Test
        @DisplayName("Issues single-use reset token on valid OTP and preserves leading zeros")
        void shouldIssueResetTokenOnValidOtp() throws Exception {
            String otpWithLeadingZero = "042918";
            String expectedHash = calculateExpectedOtpHash("finance_user", otpWithLeadingZero);

            PasswordResetToken token = PasswordResetToken.builder()
                    .user(activeUser)
                    .otpHash(expectedHash)
                    .attemptCount(0)
                    .maxAttempts(5)
                    .otpExpiresAt(LocalDateTime.now().plusMinutes(4))
                    .build();

            when(userRepository.findByUsername("finance_user")).thenReturn(Optional.of(activeUser));
            when(tokenRepository.findTopByUserAndConsumedAtIsNullOrderByCreatedAtDesc(activeUser))
                    .thenReturn(Optional.of(token));

            VerifyResetOtpResponse response = recoveryService.verifyOtp(
                    new VerifyResetOtpRequest("finance_user", otpWithLeadingZero)
            );

            assertThat(response.getResetToken()).isNotBlank();
            assertThat(response.getExpiresInSeconds()).isEqualTo(600);

            assertThat(token.isVerified()).isTrue();
            assertThat(token.getResetTokenHash()).isNotNull();
            assertThat(token.getResetTokenExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(9));
            verify(tokenRepository).save(token);
        }
    }

    @Nested
    @DisplayName("Reset Password")
    class ResetPasswordTests {

        @Test
        @DisplayName("Rejects request when new password and confirmation mismatch")
        void shouldRejectMismatchedPasswords() {
            ResetPasswordRequest request = new ResetPasswordRequest(
                    "valid-token", "NewPass123!", "DifferentPass123!"
            );

            assertThatThrownBy(() -> recoveryService.resetPassword(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("do not match");
        }

        @Test
        @DisplayName("Rejects request when new password does not meet complexity requirements")
        void shouldRejectWeakPassword() {
            ResetPasswordRequest request = new ResetPasswordRequest(
                    "valid-token", "simplepass", "simplepass"
            );

            assertThatThrownBy(() -> recoveryService.resetPassword(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Password must be at least 8 characters long");
        }

        @Test
        @DisplayName("Rejects request when reset token hash is not found")
        void shouldRejectNonExistentResetToken() {
            when(tokenRepository.findByResetTokenHashAndConsumedAtIsNull(anyString()))
                    .thenReturn(Optional.empty());

            ResetPasswordRequest request = new ResetPasswordRequest(
                    "non-existent-token", "NewPass123!", "NewPass123!"
            );

            assertThatThrownBy(() -> recoveryService.resetPassword(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("invalid or has expired");
        }

        @Test
        @DisplayName("Rejects request when reset token has expired")
        void shouldRejectExpiredResetToken() throws Exception {
            String rawToken = "my-expired-token";
            String tokenHash = calculateSha256(rawToken);

            PasswordResetToken token = PasswordResetToken.builder()
                    .user(activeUser)
                    .otpHash("otp")
                    .resetTokenHash(tokenHash)
                    .verifiedAt(LocalDateTime.now().minusMinutes(15))
                    .resetTokenExpiresAt(LocalDateTime.now().minusMinutes(5))
                    .build();

            when(tokenRepository.findByResetTokenHashAndConsumedAtIsNull(tokenHash))
                    .thenReturn(Optional.of(token));

            ResetPasswordRequest request = new ResetPasswordRequest(
                    rawToken, "NewPass123!", "NewPass123!"
            );

            assertThatThrownBy(() -> recoveryService.resetPassword(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("invalid or has expired");

            assertThat(token.isConsumed()).isTrue();
            verify(tokenRepository).save(token);
        }

        @Test
        @DisplayName("Successfully updates password, consumes token, and writes audit log")
        void shouldSuccessfullyResetPassword() throws Exception {
            String rawToken = "my-valid-token-32-bytes-entropy";
            String tokenHash = calculateSha256(rawToken);

            PasswordResetToken token = PasswordResetToken.builder()
                    .user(activeUser)
                    .otpHash("otp")
                    .resetTokenHash(tokenHash)
                    .verifiedAt(LocalDateTime.now().minusMinutes(2))
                    .resetTokenExpiresAt(LocalDateTime.now().plusMinutes(8))
                    .build();

            when(tokenRepository.findByResetTokenHashAndConsumedAtIsNull(tokenHash))
                    .thenReturn(Optional.of(token));

            ResetPasswordRequest request = new ResetPasswordRequest(
                    rawToken, "UpdatedSecurePass@2026", "UpdatedSecurePass@2026"
            );

            recoveryService.resetPassword(request);

            // Verifies password was updated and re-encoded using BCrypt
            assertThat(passwordEncoder.matches("UpdatedSecurePass@2026", activeUser.getPasswordHash())).isTrue();
            verify(userRepository).save(activeUser);

            // Verifies token was consumed
            assertThat(token.isConsumed()).isTrue();
            verify(tokenRepository).save(token);

            // Verifies audit log recorded
            verify(auditLogRepository).save(auditCaptor.capture());
            AuditLog savedLog = auditCaptor.getValue();
            assertThat(savedLog.getUser()).isEqualTo(activeUser);
            assertThat(savedLog.getAction()).isEqualTo(AuditAction.UPDATE);
            assertThat(savedLog.getEntityType()).isEqualTo("User");
            assertThat(savedLog.getEntityId()).isEqualTo(activeUser.getId());
            assertThat(savedLog.getNewValue()).contains("Password reset completed");
        }
    }
}
