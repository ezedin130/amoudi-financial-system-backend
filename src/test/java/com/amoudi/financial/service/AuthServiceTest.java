package com.amoudi.financial.service;

import com.amoudi.financial.dto.CurrentUserResponse;
import com.amoudi.financial.dto.LoginRequest;
import com.amoudi.financial.dto.LoginResponse;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.exception.UnauthorizedException;
import com.amoudi.financial.model.User;
import com.amoudi.financial.model.UserPrincipal;
import com.amoudi.financial.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private com.amoudi.financial.repository.AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;
    private UserPrincipal testPrincipal;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("finance_user")
                .passwordHash("$2a$10$hashedpassword")
                .fullName("Finance Officer")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build();
        testUser.setId(10L);

        testPrincipal = UserPrincipal.builder()
                .id(10L)
                .username("finance_user")
                .password("$2a$10$hashedpassword")
                .fullName("Finance Officer")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_FINANCE")))
                .build();

        loginRequest = LoginRequest.builder()
                .username("finance_user")
                .password("SecurePass123!")
                .build();
    }

    @Test
    @DisplayName("Should successfully authenticate active user, update last_login_at, and return JWT")
    void shouldAuthenticateActiveUserSuccessfully() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(testPrincipal);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(userRepository.findById(10L)).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(testPrincipal)).thenReturn("mock.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        LoginResponse response = authService.login(loginRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(86400L);
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getUsername()).isEqualTo("finance_user");
        assertThat(response.getUser().getRole()).isEqualTo(UserRole.FINANCE);
        assertThat(response.getUser().getStatus()).isEqualTo(UserStatus.ACTIVE);

        // Verify last_login_at was updated and persisted
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getLastLoginAt()).isNotNull();
        assertThat(userCaptor.getValue().getLastLoginAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("Should reject authentication with invalid password and NOT update last_login_at")
    void shouldRejectInvalidPasswordWithoutUpdatingLastLogin() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid username or password");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should reject authentication for non-existent user and NOT update last_login_at")
    void shouldRejectUnknownUsernameWithoutUpdatingLastLogin() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("User not found"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid username or password");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should reject authentication for INACTIVE user")
    void shouldRejectInactiveUser() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("User is disabled"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Account is inactive or locked");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should reject authentication for LOCKED user")
    void shouldRejectLockedUser() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new LockedException("User account is locked"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Account is inactive or locked");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should return safe user profile without credentials for getCurrentUser")
    void shouldReturnSafeProfileForCurrentUser() {
        CurrentUserResponse profile = authService.getCurrentUser(testPrincipal);

        assertThat(profile).isNotNull();
        assertThat(profile.getId()).isEqualTo(10L);
        assertThat(profile.getUsername()).isEqualTo("finance_user");
        assertThat(profile.getFullName()).isEqualTo("Finance Officer");
        assertThat(profile.getRole()).isEqualTo(UserRole.FINANCE);
        assertThat(profile.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should correctly represent GENERAL_MANAGER role in response")
    void shouldCorrectlyRepresentGeneralManagerRole() {
        UserPrincipal gmPrincipal = UserPrincipal.builder()
                .id(20L)
                .username("gm_user")
                .password("$2a$10$hash")
                .fullName("General Manager User")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_GENERAL_MANAGER")))
                .build();

        CurrentUserResponse profile = authService.getCurrentUser(gmPrincipal);

        assertThat(profile.getRole()).isEqualTo(UserRole.GENERAL_MANAGER);
        assertThat(profile.getUsername()).isEqualTo("gm_user");
    }
}
