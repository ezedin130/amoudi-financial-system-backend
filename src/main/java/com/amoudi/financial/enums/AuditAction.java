package com.amoudi.financial.enums;

/**
 * Defines tracked operational and financial events in the audit log.
 */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    IMPORT,
    LOGIN,
    LOGOUT,
    PAYMENT,
    PAYMENT_CANCEL,
    FILE_UPLOAD,
    FILE_DOWNLOAD,
    MAPPING_CREATE,
    MAPPING_UPDATE
}
