package com.amoudi.financial.controller;

import com.amoudi.financial.dto.ApiResponse;
import com.amoudi.financial.dto.CurrentUserResponse;
import com.amoudi.financial.dto.LoginRequest;
import com.amoudi.financial.dto.LoginResponse;
import com.amoudi.financial.exception.UnauthorizedException;
import com.amoudi.financial.model.UserPrincipal;
import com.amoudi.financial.service.AuthService;
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
 * Controller exposing authentication endpoints:
 * - POST /api/v1/auth/login : Authenticate user credentials and return JWT
 * - GET  /api/v1/auth/me    : Retrieve safe profile of currently authenticated user
 */
@RestController
@RequestMapping(Constants.AUTH_API_PREFIX)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
}
