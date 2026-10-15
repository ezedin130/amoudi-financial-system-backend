package com.amoudi.financial.service;

import com.amoudi.financial.dto.ForgotPasswordRequest;
import com.amoudi.financial.dto.ForgotPasswordResponse;
import com.amoudi.financial.dto.ResetPasswordRequest;
import com.amoudi.financial.dto.VerifyResetOtpRequest;
import com.amoudi.financial.dto.VerifyResetOtpResponse;

/**
 * Service managing secure self-service password recovery workflows.
 */
public interface PasswordRecoveryService {

    /**
     * Initiates the recovery workflow by generating a 6-digit OTP sent to the shared company inbox.
     * Enforces anti-enumeration semantics by returning a uniform response.
     */
    ForgotPasswordResponse requestOtp(ForgotPasswordRequest request);

    /**
     * Verifies the 6-digit OTP against the stored cryptographic digest, bounded by 5 attempts.
     * On success, issues a single-use reset token valid for 10 minutes.
     */
    VerifyResetOtpResponse verifyOtp(VerifyResetOtpRequest request);

    /**
     * Updates the user's password using a verified single-use reset token and records an audit log.
     */
    void resetPassword(ResetPasswordRequest request);
}
