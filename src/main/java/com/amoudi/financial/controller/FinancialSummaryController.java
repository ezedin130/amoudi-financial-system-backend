package com.amoudi.financial.controller;

import com.amoudi.financial.dto.ApiResponse;
import com.amoudi.financial.dto.CompanyFinancialOverviewResponse;
import com.amoudi.financial.dto.FinancialSummaryResponse;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.service.BalanceService;
import com.amoudi.financial.utils.Constants;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for financial summaries and outstanding balance metrics across Non-IATA accounts.
 *
 * All balances are derived dynamically from transactional records:
 * OUTSTANDING BALANCE = TOTAL SALES - TOTAL POSTED PAYMENTS
 *
 * Accessible by both FINANCE and GENERAL_MANAGER roles.
 */
@RestController
@RequestMapping(Constants.FINANCIAL_SUMMARIES_API_PREFIX)
public class FinancialSummaryController {

    private final BalanceService balanceService;

    public FinancialSummaryController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    /**
     * Retrieves company-wide financial overview (Total Sales, Total Payments, Outstanding Balance).
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<CompanyFinancialOverviewResponse>> getCompanyFinancialOverview() {
        CompanyFinancialOverviewResponse overview = balanceService.getCompanyFinancialOverview();
        return ResponseEntity.ok(ApiResponse.ok("Company financial overview retrieved successfully", overview));
    }

    /**
     * Retrieves paginated financial summaries for Non-IATA accounts using database aggregation.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<FinancialSummaryResponse>>> listFinancialSummaries(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) NonIataStatus status,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<FinancialSummaryResponse> result = balanceService.listFinancialSummaries(search, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok("Financial summaries retrieved successfully", result));
    }

    /**
     * Retrieves financial summary for an individual Non-IATA account by Non-IATA ID.
     */
    @GetMapping("/{nonIataId}")
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<FinancialSummaryResponse>> getFinancialSummary(@PathVariable Long nonIataId) {
        FinancialSummaryResponse summary = balanceService.getFinancialSummary(nonIataId);
        return ResponseEntity.ok(ApiResponse.ok("Financial summary retrieved successfully", summary));
    }
}
