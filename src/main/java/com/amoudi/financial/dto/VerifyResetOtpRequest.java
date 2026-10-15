package com.amoudi.financial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload to verify the 6-digit OTP sent to the shared company inbox.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyResetOtpRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "OTP verification code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "Verification code must be exactly 6 numeric digits")
    private String otp;
}
