package com.amoudi.financial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response returned upon successful OTP verification, providing a single-use
 * reset token for setting a new password.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyResetOtpResponse {

    private String resetToken;
    private long expiresInSeconds;
}
