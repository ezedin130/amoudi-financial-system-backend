package com.amoudi.financial.enums;

/**
 * Defines the ingestion and reconciliation lifecycle stages of an uploaded sales batch.
 */
public enum ImportStatus {
    UPLOADED,
    PROCESSING,
    VALIDATION_FAILED,
    READY,
    IMPORTED,
    PARTIALLY_IMPORTED,
    FAILED
}
