package com.amoudi.financial.dto;

import com.amoudi.financial.enums.ImportErrorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response payload representing a row-level import error in a batch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportBatchErrorResponse {
    private Long id;
    private Long importBatchId;
    private int rowNumber;
    private String ticketNumber;
    private String sourceAgencyName;
    private ImportErrorType errorType;
    private String message;
    private LocalDateTime createdAt;
}
