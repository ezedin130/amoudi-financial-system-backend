package com.amoudi.financial.service;

import com.amoudi.financial.dto.CurrentUserResponse;
import com.amoudi.financial.dto.LoginRequest;
import com.amoudi.financial.dto.LoginResponse;
import com.amoudi.financial.model.UserPrincipal;

/**
 * Service handling authentication operations: credential validation,
 * JWT token issuance, login tracking, and current user retrieval.
 */
public interface AuthService {

    /**
     * Authenticates credentials, updates last_login_at, and issues a JWT token.
     */
    LoginResponse login(LoginRequest request);

    /**
     * Returns the safe profile for the currently authenticated principal.
     */
    CurrentUserResponse getCurrentUser(UserPrincipal principal);

    /**
     * Returns the safe profile for the user identified by username.
     */
    CurrentUserResponse getCurrentUserByUsername(String username);
}
