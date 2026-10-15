package com.amoudi.financial.service;

import com.amoudi.financial.dto.ForgotPasswordRequest;
import com.amoudi.financial.dto.ForgotPasswordResponse;
import com.amoudi.financial.dto.ResetPasswordRequest;
import com.amoudi.financial.dto.VerifyResetOtpRequest;
import com.amoudi.financial.dto.VerifyResetOtpResponse;
import com.amoudi.financial.enums.AuditAction;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.exception.BadRequestException;
import com.amoudi.financial.model.AuditLog;
import com.amoudi.financial.model.PasswordResetToken;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.AuditLogRepository;
import com.amoudi.financial.repository.PasswordResetTokenRepository;
import com.amoudi.financial.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Implementation of PasswordRecoveryService providing cryptographic OTP issuance,
 * attempt-bounded verification, single-use reset token exchange, and password updates.
 */
@Slf4j
@Service
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {

    public static final String GENERIC_REQUEST_RESPONSE =
            "If an active account exists for that username, a 6-digit verification code has been sent to the company recovery email address.";

    private static final int OTP_VALIDITY_MINUTES = 5;
    private static final int RESET_TOKEN_VALIDITY_MINUTES = 10;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int MIN_RESEND_COOLDOWN_SECONDS = 30;

    // Password complexity: min 8 characters, at least 1 uppercase, 1 lowercase, 1 number, 1 special character
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,100}$"
    );

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogRepository auditLogRepository;
    private final SecureRandom secureRandom;
    private final byte[] secretPepper;

    public PasswordRecoveryServiceImpl(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            AuditLogRepository auditLogRepository,
            @Value("${app.jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secret
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogRepository = auditLogRepository;
        this.secureRandom = new SecureRandom();
        this.secretPepper = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Transactional
    public ForgotPasswordResponse requestOtp(ForgotPasswordRequest request) {
        String normalizedUsername = request.getUsername().trim();
        log.info("Password recovery requested for username '{}'", normalizedUsername);

        Optional<User> userOpt = userRepository.findByUsername(normalizedUsername);

        if (userOpt.isEmpty()) {
            log.warn("Password recovery requested for non-existent user '{}'. Returning generic response.", normalizedUsername);
            return new ForgotPasswordResponse(GENERIC_REQUEST_RESPONSE);
        }

        User user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Password recovery requested for inactive/locked user '{}' (Status: {}). Returning generic response.",
                    normalizedUsername, user.getStatus());
            return new ForgotPasswordResponse(GENERIC_REQUEST_RESPONSE);
        }

        // Rate limit: check if a token was created recently
        Optional<PasswordResetToken> lastTokenOpt = tokenRepository.findTopByUserAndConsumedAtIsNullOrderByCreatedAtDesc(user);
        if (lastTokenOpt.isPresent()) {
            PasswordResetToken lastToken = lastTokenOpt.get();
            if (lastToken.getCreatedAt() != null &&
                    lastToken.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(MIN_RESEND_COOLDOWN_SECONDS))) {
                log.warn("Rate limit triggered for user '{}'. OTP requested too soon.", normalizedUsername);
                throw new BadRequestException("RATE_LIMITED",
                        "A verification code was recently generated. Please wait 30 seconds before requesting a new code.");
            }
        }

        // Invalidate any previously unconsumed tokens for this user
        List<PasswordResetToken> activeTokens = tokenRepository.findByUserAndConsumedAtIsNull(user);
        for (PasswordResetToken activeToken : activeTokens) {
            activeToken.setConsumedAt(LocalDateTime.now());
        }
        tokenRepository.saveAll(activeTokens);

        // Generate 6-digit numeric OTP with leading zeros preserved
        int numericCode = secureRandom.nextInt(1_000_000);
        String otp = String.format("%06d", numericCode);

        // Compute HMAC-SHA256 digest of the OTP
        String otpDigest = hashOtp(user.getUsername(), otp);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .otpHash(otpDigest)
                .attemptCount(0)
                .maxAttempts(MAX_OTP_ATTEMPTS)
                .otpExpiresAt(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES))
                .build();

        tokenRepository.save(resetToken);

        // Dispatch email to shared company inbox
        emailService.sendPasswordRecoveryOtp(user.getUsername(), otp, OTP_VALIDITY_MINUTES);

        return new ForgotPasswordResponse(GENERIC_REQUEST_RESPONSE);
    }

    @Override
    @Transactional
    public VerifyResetOtpResponse verifyOtp(VerifyResetOtpRequest request) {
        String normalizedUsername = request.getUsername().trim();
        String candidateOtp = request.getOtp().trim();

        User user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new BadRequestException("INVALID_OTP", "The verification code is invalid or has expired."));

        PasswordResetToken token = tokenRepository.findTopByUserAndConsumedAtIsNullOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new BadRequestException("INVALID_OTP", "The verification code is invalid or has expired."));

        if (token.isOtpExpired()) {
            token.setConsumedAt(LocalDateTime.now());
            tokenRepository.save(token);
            throw new BadRequestException("INVALID_OTP", "The verification code has expired. Please request a new code.");
        }

        if (token.isMaxAttemptsReached()) {
            token.setConsumedAt(LocalDateTime.now());
            tokenRepository.save(token);
            throw new BadRequestException("OTP_ATTEMPTS_EXCEEDED",
                    "Maximum verification attempts exceeded. Please request a new verification code.");
        }

        String candidateDigest = hashOtp(user.getUsername(), candidateOtp);
        if (!MessageDigest.isEqual(token.getOtpHash().getBytes(StandardCharsets.UTF_8),
                candidateDigest.getBytes(StandardCharsets.UTF_8))) {
            token.setAttemptCount(token.getAttemptCount() + 1);

            int remaining = token.getMaxAttempts() - token.getAttemptCount();
            if (remaining <= 0) {
                token.setConsumedAt(LocalDateTime.now());
                tokenRepository.save(token);
                throw new BadRequestException("OTP_ATTEMPTS_EXCEEDED",
                        "Maximum verification attempts exceeded. Please request a new verification code.");
            }
            tokenRepository.save(token);
            throw new BadRequestException("INVALID_OTP",
                    "Invalid verification code. Attempts remaining: " + remaining);
        }

        // OTP verified successfully: issue unpredictable 256-bit single-use reset token
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawResetToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String resetTokenHash = sha256Hex(rawResetToken);

        token.setVerifiedAt(LocalDateTime.now());
        token.setResetTokenHash(resetTokenHash);
        token.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_VALIDITY_MINUTES));
        tokenRepository.save(token);

        log.info("OTP verified successfully for user '{}'. Single-use reset token issued.", user.getUsername());

        return VerifyResetOtpResponse.builder()
                .resetToken(rawResetToken)
                .expiresInSeconds(RESET_TOKEN_VALIDITY_MINUTES * 60L)
                .build();
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String rawResetToken = request.getResetToken().trim();
        String newPassword = request.getNewPassword();
        String confirmPassword = request.getConfirmPassword();

        if (!newPassword.equals(confirmPassword)) {
            throw new BadRequestException("PASSWORD_MISMATCH", "New password and confirmation password do not match.");
        }

        if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
            throw new BadRequestException("WEAK_PASSWORD",
                    "Password must be at least 8 characters long and contain at least one uppercase letter, " +
                    "one lowercase letter, one number, and one special character.");
        }

        String resetTokenHash = sha256Hex(rawResetToken);
        PasswordResetToken token = tokenRepository.findByResetTokenHashAndConsumedAtIsNull(resetTokenHash)
                .orElseThrow(() -> new BadRequestException("INVALID_RESET_TOKEN",
                        "The password reset token is invalid or has expired. Please restart the recovery process."));

        if (!token.isVerified() || token.isResetTokenExpired()) {
            token.setConsumedAt(LocalDateTime.now());
            tokenRepository.save(token);
            throw new BadRequestException("INVALID_RESET_TOKEN",
                    "The password reset token is invalid or has expired. Please restart the recovery process.");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Invalidate the reset token permanently
        token.setConsumedAt(LocalDateTime.now());
        tokenRepository.save(token);

        // Record audit trail
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .action(AuditAction.UPDATE)
                    .entityType("User")
                    .entityId(user.getId())
                    .newValue("Password reset completed successfully via recovery OTP verification")
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.warn("Failed to record password reset audit log for user '{}': {}", user.getUsername(), ex.getMessage());
        }

        log.info("Password successfully reset for user '{}' (ID: {})", user.getUsername(), user.getId());
    }

    private String hashOtp(String username, String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretPepper, "HmacSHA256"));
            byte[] hash = mac.doFinal((username.toLowerCase() + ":" + otp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 digest for OTP", ex);
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
