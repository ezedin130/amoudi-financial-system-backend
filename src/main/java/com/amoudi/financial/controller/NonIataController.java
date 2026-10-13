package com.amoudi.financial.controller;

import com.amoudi.financial.dto.ApiResponse;
import com.amoudi.financial.dto.NonIataCreateRequest;
import com.amoudi.financial.dto.NonIataResponse;
import com.amoudi.financial.dto.NonIataStatusUpdateRequest;
import com.amoudi.financial.dto.NonIataUpdateRequest;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.dto.FinancialSummaryResponse;
import com.amoudi.financial.service.BalanceService;
import com.amoudi.financial.service.NonIataService;
import com.amoudi.financial.utils.Constants;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Non-IATA sub-agent management.
 *
 * Authorization rules:
 * - Read endpoints: accessible by FINANCE and GENERAL_MANAGER
 * - Write endpoints: strictly restricted to FINANCE
 * - Physical deletion: strictly PROHIBITED (no DELETE endpoint)
 */
@RestController
@RequestMapping(Constants.NON_IATAS_API_PREFIX)
public class NonIataController {

    private final NonIataService nonIataService;
    private final BalanceService balanceService;

    public NonIataController(NonIataService nonIataService, BalanceService balanceService) {
        this.nonIataService = nonIataService;
        this.balanceService = balanceService;
    }

    /**
     * Lists Non-IATA accounts with optional search by code/name, status filter, and pagination.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<NonIataResponse>>> listNonIatas(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) NonIataStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<NonIataResponse> result = nonIataService.listNonIatas(search, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok("Non-IATA accounts retrieved successfully", result));
    }

    /**
     * Retrieves single Non-IATA account details by database ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<NonIataResponse>> getNonIataById(@PathVariable Long id) {
        NonIataResponse response = nonIataService.getNonIataById(id);
        return ResponseEntity.ok(ApiResponse.ok("Non-IATA account retrieved successfully", response));
    }

    /**
     * Creates a new Non-IATA account.
     * Code must be unique. Initial status will be ACTIVE.
     */
    @PostMapping
    @PreAuthorize("hasRole('FINANCE')")
    public ResponseEntity<ApiResponse<NonIataResponse>> createNonIata(@Valid @RequestBody NonIataCreateRequest request) {
        NonIataResponse created = nonIataService.createNonIata(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Non-IATA account created successfully", created));
    }

    /**
     * Updates an existing Non-IATA account details.
     * Note: Code is immutable and cannot be updated.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FINANCE')")
    public ResponseEntity<ApiResponse<NonIataResponse>> updateNonIata(
            @PathVariable Long id,
            @Valid @RequestBody NonIataUpdateRequest request
    ) {
        NonIataResponse updated = nonIataService.updateNonIata(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Non-IATA account updated successfully", updated));
    }

    /**
     * Updates the operational status (ACTIVE / INACTIVE) of a Non-IATA account.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('FINANCE')")
    public ResponseEntity<ApiResponse<NonIataResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody NonIataStatusUpdateRequest request
    ) {
        NonIataResponse updated = nonIataService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Non-IATA status updated successfully", updated));
    }

    /**
     * Retrieves the dynamic financial summary (sales, payments, outstanding balance)
     * for an individual Non-IATA account.
     */
    @GetMapping("/{id}/financial-summary")
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<FinancialSummaryResponse>> getFinancialSummary(@PathVariable Long id) {
        FinancialSummaryResponse summary = balanceService.getFinancialSummary(id);
        return ResponseEntity.ok(ApiResponse.ok("Financial summary retrieved successfully", summary));
    }
}
