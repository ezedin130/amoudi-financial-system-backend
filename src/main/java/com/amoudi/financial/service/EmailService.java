package com.amoudi.financial.service;

/**
 * Service for delivering system notifications and password recovery emails.
 */
public interface EmailService {

    /**
     * Sends a 6-digit password recovery OTP to the configured shared company inbox.
     *
     * @param username        The username of the account requesting reset
     * @param otp             The 6-digit one-time password
     * @param validityMinutes Validity window in minutes
     */
    void sendPasswordRecoveryOtp(String username, String otp, int validityMinutes);
}
