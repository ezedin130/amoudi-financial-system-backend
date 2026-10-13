package com.amoudi.financial.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Utility functions for consistent date-time processing and formatting.
 */
public final class DateTimeUtils {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private DateTimeUtils() {
        // Prevent instantiation
    }

    /**
     * Formats the current time as an ISO-8601 UTC timestamp string.
     *
     * @return current ISO-8601 timestamp string (e.g. 2026-09-29T08:30:00Z)
     */
    public static String nowUtcIso() {
        return ISO_FORMATTER.format(Instant.now());
    }

    /**
     * Formats a given LocalDateTime in UTC as an ISO-8601 string.
     *
     * @param dateTime the LocalDateTime instance
     * @return formatted ISO-8601 string, or null if dateTime is null
     */
    public static String toUtcIso(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return ISO_FORMATTER.format(dateTime.toInstant(ZoneOffset.UTC));
    }
}
