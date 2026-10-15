package com.amoudi.financial.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Entity tracking one-time password (OTP) verification and single-use reset tokens
 * for secure self-service password recovery.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash;

    @Column(name = "reset_token_hash", length = 255)
    private String resetTokenHash;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Builder.Default
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "otp_expires_at", nullable = false)
    private LocalDateTime otpExpiresAt;

    @Column(name = "reset_token_expires_at")
    private LocalDateTime resetTokenExpiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    public boolean isOtpExpired() {
        return otpExpiresAt == null || LocalDateTime.now().isAfter(otpExpiresAt);
    }

    public boolean isResetTokenExpired() {
        return resetTokenExpiresAt == null || LocalDateTime.now().isAfter(resetTokenExpiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public boolean isMaxAttemptsReached() {
        return attemptCount >= maxAttempts;
    }
}
