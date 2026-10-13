package com.amoudi.financial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Filter criteria for querying sales transactions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesFilterRequest {

    private Long nonIataId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate saleDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private String ticketNumber;
    private String transactionReference;
    private Long importBatchId;
}
