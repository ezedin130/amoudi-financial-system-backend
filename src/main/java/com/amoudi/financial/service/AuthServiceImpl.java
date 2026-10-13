package com.amoudi.financial.service;

import com.amoudi.financial.dto.CurrentUserResponse;
import com.amoudi.financial.dto.LoginRequest;
import com.amoudi.financial.dto.LoginResponse;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.exception.ResourceNotFoundException;
import com.amoudi.financial.exception.UnauthorizedException;
import com.amoudi.financial.model.User;
import com.amoudi.financial.model.UserPrincipal;
import com.amoudi.financial.enums.AuditAction;
import com.amoudi.financial.model.AuditLog;
import com.amoudi.financial.repository.AuditLogRepository;
import com.amoudi.financial.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Implementation of AuthService coordinating authentication, login auditing,
 * and JWT generation.
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuditLogRepository auditLogRepository;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            AuditLogRepository auditLogRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (DisabledException | LockedException ex) {
            log.warn("Authentication rejected for user '{}': account is disabled or locked ({})",
                    request.getUsername(), ex.getClass().getSimpleName());
            throw new UnauthorizedException("Account is inactive or locked");
        } catch (BadCredentialsException ex) {
            log.warn("Authentication failed for user '{}': invalid credentials", request.getUsername());
            throw new UnauthorizedException("Invalid username or password");
        } catch (AuthenticationException ex) {
            log.warn("Authentication exception for user '{}': {}", request.getUsername(), ex.getMessage());
            throw new UnauthorizedException("Invalid username or password");
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        // Extra status safety check
        if (principal.getStatus() != UserStatus.ACTIVE) {
            log.warn("User '{}' status is not ACTIVE ({})", principal.getUsername(), principal.getStatus());
            throw new UnauthorizedException("Account is inactive or locked");
        }

        // On successful authentication, update last_login_at
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("User account not found"));

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Record Audit Log for successful login
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .action(AuditAction.LOGIN)
                    .entityType("User")
                    .entityId(user.getId())
                    .newValue("User '" + user.getUsername() + "' logged in successfully")
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to record login audit log for user {}: {}", user.getUsername(), e.getMessage());
        }

        log.info("User '{}' (ID: {}, Role: {}) authenticated successfully",
                user.getUsername(), user.getId(), user.getRole());

        // Generate signed JWT access token
        String accessToken = jwtService.generateToken(principal);

        CurrentUserResponse safeProfile = CurrentUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .build();

        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .user(safeProfile)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(UserPrincipal principal) {
        return CurrentUserResponse.builder()
                .id(principal.getId())
                .username(principal.getUsername())
                .fullName(principal.getFullName())
                .role(principal.getRole())
                .status(principal.getStatus())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        return CurrentUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
    }
}
