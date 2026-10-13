package com.amoudi.financial.filter;

import com.amoudi.financial.utils.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Filter that establishes an MDC correlation ID for end-to-end request tracing.
 * Captures and sanitizes X-Correlation-ID from the request or generates a new UUID if omitted or invalid.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Pattern SAFE_CORRELATION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(Constants.CORRELATION_ID_HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = request.getHeader(Constants.REQUEST_ID_HEADER);
        }

        if (!StringUtils.hasText(correlationId) || !SAFE_CORRELATION_ID_PATTERN.matcher(correlationId.trim()).matches()) {
            correlationId = UUID.randomUUID().toString();
        } else {
            correlationId = correlationId.trim();
        }

        MDC.put(Constants.CORRELATION_ID_MDC_KEY, correlationId);
        MDC.put(Constants.REQUEST_ID_MDC_KEY, correlationId);
        response.setHeader(Constants.CORRELATION_ID_HEADER, correlationId);
        response.setHeader(Constants.REQUEST_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(Constants.CORRELATION_ID_MDC_KEY);
            MDC.remove(Constants.REQUEST_ID_MDC_KEY);
        }
    }
}
