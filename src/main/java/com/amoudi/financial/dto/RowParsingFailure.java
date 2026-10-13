package com.amoudi.financial.dto;

import com.amoudi.financial.enums.ImportErrorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a failure encountered while reading/parsing an Excel row.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RowParsingFailure {
    private int rowNumber;
    private String ticketNumber;
    private String sourceAgencyName;
    private ImportErrorType errorType;
    private String message;
}
