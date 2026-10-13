package com.amoudi.financial.utils;

/**
 * Global application constants.
 */
public final class Constants {

    private Constants() {
        // Prevent instantiation
    }

    public static final String API_V1_PREFIX = "/api/v1";
    public static final String HEALTH_ENDPOINT = API_V1_PREFIX + "/health";
    public static final String AUTH_API_PREFIX = API_V1_PREFIX + "/auth";
    public static final String AUTH_LOGIN_ENDPOINT = AUTH_API_PREFIX + "/login";
    public static final String AUTH_ME_ENDPOINT = AUTH_API_PREFIX + "/me";
    public static final String NON_IATAS_API_PREFIX = API_V1_PREFIX + "/non-iatas";
    public static final String SALES_API_PREFIX = API_V1_PREFIX + "/sales";
    public static final String FINANCIAL_SUMMARIES_API_PREFIX = API_V1_PREFIX + "/financial-summaries";
    public static final String IMPORTS_API_PREFIX = API_V1_PREFIX + "/imports";
    public static final String NAME_MAPPINGS_API_PREFIX = API_V1_PREFIX + "/name-mappings";
    public static final String PAYMENTS_API_PREFIX = API_V1_PREFIX + "/payments";
    public static final String AGING_API_PREFIX = API_V1_PREFIX + "/aging";
    public static final String AUDIT_LOGS_API_PREFIX = API_V1_PREFIX + "/audit-logs";
    public static final String DEFAULT_CURRENCY = "ETB";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String TOKEN_BEARER_PREFIX = "Bearer ";
    public static final String ROLE_PREFIX = "ROLE_";
}
