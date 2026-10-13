package com.amoudi.financial.enums;

/**
 * Defines the classification of errors encountered during row ingestion.
 */
public enum ImportErrorType {
    UNMATCHED_AGENCY,
    DUPLICATE_TRANSACTION,
    INVALID_AMOUNT,
    INVALID_DATE,
    MISSING_REQUIRED_FIELD,
    PARSING_ERROR,
    VALIDATION_ERROR
}
