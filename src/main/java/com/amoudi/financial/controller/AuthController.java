package com.amoudi.financial.controller;

import com.amoudi.financial.dto.ApiResponse;
import com.amoudi.financial.dto.CurrentUserResponse;
import com.amoudi.financial.dto.LoginRequest;
import com.amoudi.financial.dto.LoginResponse;
import com.amoudi.financial.dto.ForgotPasswordRequest;
import com.amoudi.financial.dto.ForgotPasswordResponse;
import com.amoudi.financial.dto.ResetPasswordRequest;
import com.amoudi.financial.dto.VerifyResetOtpRequest;
import com.amoudi.financial.dto.VerifyResetOtpResponse;
import com.amoudi.financial.exception.UnauthorizedException;
import com.amoudi.financial.model.UserPrincipal;
import com.amoudi.financial.service.AuthService;
import com.amoudi.financial.service.PasswordRecoveryService;
import com.amoudi.financial.utils.Constants;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing authentication and recovery endpoints:
 * - POST /api/v1/auth/login            : Authenticate user credentials and return JWT
 * - GET  /api/v1/auth/me               : Retrieve safe profile of currently authenticated user
 * - POST /api/v1/auth/forgot-password  : Initiate password recovery with OTP sent to shared inbox
 * - POST /api/v1/auth/verify-reset-otp : Verify 6-digit OTP and obtain single-use reset token
 * - POST /api/v1/auth/reset-password   : Set new password using verified single-use reset token
 */
@RestController
@RequestMapping(Constants.AUTH_API_PREFIX)
public class AuthController {

    private final AuthService authService;
    private final PasswordRecoveryService passwordRecoveryService;

    public AuthController(AuthService authService, PasswordRecoveryService passwordRecoveryService) {
        this.authService = authService;
        this.passwordRecoveryService = passwordRecoveryService;
    }

    /**
     * Authenticates a user with username and password.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Authentication successful", response));
    }

    /**
     * Returns the safe profile of the currently authenticated user.
     * Requires valid Bearer JWT.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication is required to access profile");
        }

        CurrentUserResponse profile = authService.getCurrentUser(principal);
        return ResponseEntity.ok(ApiResponse.ok("User profile retrieved successfully", profile));
    }

    /**
     * Initiates password recovery. Dispatches a 6-digit OTP to the shared company recovery inbox.
     * Always returns a generic success response to prevent username enumeration.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        ForgotPasswordResponse response = passwordRecoveryService.requestOtp(request);
        return ResponseEntity.ok(ApiResponse.ok(response.getMessage(), response));
    }

    /**
     * Verifies the submitted 6-digit OTP against the stored cryptographic digest.
     * On success, generates a single-use password reset token valid for 10 minutes.
     */
    @PostMapping("/verify-reset-otp")
    public ResponseEntity<ApiResponse<VerifyResetOtpResponse>> verifyResetOtp(
            @Valid @RequestBody VerifyResetOtpRequest request
    ) {
        VerifyResetOtpResponse response = passwordRecoveryService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("Verification code verified successfully", response));
    }

    /**
     * Sets a new password using a validated single-use reset token and records an audit log.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        passwordRecoveryService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password has been successfully reset. Please log in with your new password.", null));
    }
}

