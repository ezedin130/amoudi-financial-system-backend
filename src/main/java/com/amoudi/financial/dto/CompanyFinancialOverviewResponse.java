package com.amoudi.financial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Company-wide financial overview response representing aggregate figures:
 * Total Sales (SUM of Grand Total), Total Payments (SUM of POSTED payments),
 * and Outstanding Balance (Total Sales - Total Payments).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyFinancialOverviewResponse {

    private BigDecimal totalSales;
    private BigDecimal totalPayments;
    private BigDecimal outstandingBalance;
    private long activeAccountsCount;
    private long totalAccountsCount;
    private String currency;
}
