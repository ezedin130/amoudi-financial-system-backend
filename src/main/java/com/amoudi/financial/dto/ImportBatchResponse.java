package com.amoudi.financial.dto;

import com.amoudi.financial.enums.ImportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response payload representing an import batch and its ingestion metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportBatchResponse {
    private Long id;
    private String fileName;
    private String objectKey;
    private LocalDate reportDate;
    private Long uploadedById;
    private String uploadedByUsername;
    private LocalDateTime uploadedAt;
    private int totalRows;
    private int successfulRows;
    private int duplicateRows;
    private int unmatchedRows;
    private int failedRows;
    private int salesCount;
    private int refundsCount;
    private int exchangesCount;
    private ImportStatus status;
    private String errorSummary;
    private LocalDateTime createdAt;
}
