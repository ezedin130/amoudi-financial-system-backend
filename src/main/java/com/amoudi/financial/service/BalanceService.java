package com.amoudi.financial.service;

import com.amoudi.financial.dto.FinancialSummaryResponse;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.enums.NonIataStatus;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for calculating financial balances and account summaries.
 * Designed around the core financial formula:
 * OUTSTANDING BALANCE = TOTAL SALES - TOTAL POSTED PAYMENTS
 */
public interface BalanceService {

    /**
     * Calculates the financial summary for a single Non-IATA account.
     *
     * @param nonIataId Target Non-IATA ID
     * @return FinancialSummaryResponse with derived totalSales, totalPayments, and outstandingBalance
     */
    FinancialSummaryResponse getFinancialSummary(Long nonIataId);

    /**
     * Lists paginated financial summaries across all Non-IATA accounts using database-level aggregation.
     *
     * @param search Optional search term matching code or name
     * @param status Optional NonIataStatus filter
     * @param pageable Pagination and sorting options
     * @return Paginated FinancialSummaryResponse list
     */
    PageResponse<FinancialSummaryResponse> listFinancialSummaries(String search, NonIataStatus status, Pageable pageable);

    /**
     * Calculates the company-wide financial overview across all Non-IATA accounts:
     * Total Sales = SUM(Grand Total)
     * Total Payments = SUM(POSTED Payments)
     * Outstanding Balance = Total Sales - Total Payments
     */
    com.amoudi.financial.dto.CompanyFinancialOverviewResponse getCompanyFinancialOverview();
}
