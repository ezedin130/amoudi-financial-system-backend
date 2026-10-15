package com.amoudi.financial.config;

import com.amoudi.financial.dto.ErrorResponse;
import com.amoudi.financial.filter.JwtAuthenticationFilter;
import com.amoudi.financial.service.CustomUserDetailsService;
import com.amoudi.financial.utils.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security Configuration.
 *
 * Configures stateless session policy, JWT token authentication filter,
 * public authentication and health endpoints, role-based authorization,
 * BCrypt password hashing, and unified JSON error handling for 401 and 403 responses.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final ObjectMapper objectMapper;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(
            CorsConfigurationSource corsConfigurationSource,
            ObjectMapper objectMapper,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomUserDetailsService userDetailsService
    ) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.objectMapper = objectMapper;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for stateless RESTful APIs
                .csrf(AbstractHttpConfigurer::disable)

                // Attach configured CORS rules
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // Enforce stateless session policy
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Configure endpoint authorization boundaries
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints strictly constrained by HTTP method
                        .requestMatchers(HttpMethod.GET, Constants.HEALTH_ENDPOINT).permitAll()
                        .requestMatchers(HttpMethod.POST, Constants.AUTH_LOGIN_ENDPOINT).permitAll()
                        .requestMatchers(HttpMethod.POST, Constants.AUTH_FORGOT_PASSWORD_ENDPOINT).permitAll()
                        .requestMatchers(HttpMethod.POST, Constants.AUTH_VERIFY_RESET_OTP_ENDPOINT).permitAll()
                        .requestMatchers(HttpMethod.POST, Constants.AUTH_RESET_PASSWORD_ENDPOINT).permitAll()
                        .requestMatchers("/error").permitAll()
                        // Protected endpoints (including /api/v1/auth/me) require authentication
                        .anyRequest().authenticated()
                )

                // Register DaoAuthenticationProvider
                .authenticationProvider(authenticationProvider())

                // Add JWT filter before UsernamePasswordAuthenticationFilter in SecurityFilterChain
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // Uniform JSON error on unauthenticated access attempts or insufficient permissions
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(restAuthenticationEntryPoint())
                        .accessDeniedHandler(restAccessDeniedHandler())
                )

                // HTTP Security Headers
                .headers(headers -> headers
                        .contentTypeOptions(contentType -> {})
                        .frameOptions(frame -> frame.deny())
                        .xssProtection(xss -> xss.disable())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                );

        return http.build();
    }

    /**
     * Prevents Spring Boot from auto-registering JwtAuthenticationFilter into the top-level
     * servlet container filter chain, ensuring it only executes once within SecurityFilterChain.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Configures DaoAuthenticationProvider with UserDetailsService and PasswordEncoder.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Exposes AuthenticationManager bean for authentication operations.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Authentication entry point returning standard ErrorResponse JSON structure
     * when an unauthenticated request attempts to access a protected resource (HTTP 401).
     */
    @Bean
    public AuthenticationEntryPoint restAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .status(HttpStatus.UNAUTHORIZED.value())
                    .error("UNAUTHORIZED")
                    .message("Authentication is required to access this resource")
                    .path(request.getRequestURI())
                    .correlationId(org.slf4j.MDC.get(Constants.CORRELATION_ID_MDC_KEY))
                    .build();

            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        };
    }

    /**
     * Access denied handler returning standard ErrorResponse JSON structure
     * when an authenticated request lacks required privileges (HTTP 403).
     */
    @Bean
    public AccessDeniedHandler restAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .status(HttpStatus.FORBIDDEN.value())
                    .error("ACCESS_DENIED")
                    .message("Access is denied")
                    .path(request.getRequestURI())
                    .correlationId(org.slf4j.MDC.get(Constants.CORRELATION_ID_MDC_KEY))
                    .build();

            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        };
    }

    /**
     * Password encoder bean utilizing BCrypt adaptive hashing.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
