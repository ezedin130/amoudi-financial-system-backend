package com.amoudi.financial.controller;

import com.amoudi.financial.dto.ApiResponse;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.dto.SalesFilterRequest;
import com.amoudi.financial.dto.SalesTransactionResponse;
import com.amoudi.financial.service.SalesService;
import com.amoudi.financial.utils.Constants;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST controller for querying individual ticket sales transactions.
 *
 * Immutability & Safety Rules:
 * - Read-only endpoints accessible by both FINANCE and GENERAL_MANAGER roles.
 * - Sales transactions are system-imported historical records.
 * - POST, PUT, PATCH, DELETE are strictly prohibited (no write endpoints exposed).
 */
@RestController
@RequestMapping(Constants.SALES_API_PREFIX)
public class SalesController {

    private final SalesService salesService;

    public SalesController(SalesService salesService) {
        this.salesService = salesService;
    }

    /**
     * Lists sales transactions with dynamic filtering, pagination, and safe sorting.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<SalesTransactionResponse>>> listSales(
            @RequestParam(required = false) Long nonIataId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate saleDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String ticketNumber,
            @RequestParam(required = false) String transactionReference,
            @RequestParam(required = false) Long importBatchId,
            @PageableDefault(size = 20, sort = "saleDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        SalesFilterRequest filter = SalesFilterRequest.builder()
                .nonIataId(nonIataId)
                .saleDate(saleDate)
                .startDate(startDate)
                .endDate(endDate)
                .ticketNumber(ticketNumber)
                .transactionReference(transactionReference)
                .importBatchId(importBatchId)
                .build();

        PageResponse<SalesTransactionResponse> result = salesService.listSales(filter, pageable);
        return ResponseEntity.ok(ApiResponse.ok("Sales transactions retrieved successfully", result));
    }

    /**
     * Retrieves a single sales transaction by its database ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<SalesTransactionResponse>> getSaleById(@PathVariable Long id) {
        SalesTransactionResponse response = salesService.getSaleById(id);
        return ResponseEntity.ok(ApiResponse.ok("Sales transaction retrieved successfully", response));
    }
}
