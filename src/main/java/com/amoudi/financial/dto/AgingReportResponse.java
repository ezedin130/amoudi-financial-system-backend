package com.amoudi.financial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * High-level aging report aggregating balances across aging buckets for Non-IATA accounts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgingReportResponse {

    private LocalDate asOfDate;
    private BigDecimal totalCurrent;
    private BigDecimal totalDays31To60;
    private BigDecimal totalDays61To90;
    private BigDecimal totalDays91To120;
    private BigDecimal totalDays120Plus;
    private BigDecimal totalOutstanding;
    private BigDecimal totalSales;
    private BigDecimal totalPayments;
    private String currency;
    private int totalAccounts;
    private List<AgingRowResponse> rows;
}
