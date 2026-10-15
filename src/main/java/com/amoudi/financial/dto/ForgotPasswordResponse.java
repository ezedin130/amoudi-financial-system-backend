package com.amoudi.financial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public response returned after requesting password recovery OTP.
 * Intentionally generic to prevent user enumeration attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordResponse {

    private String message;
}
