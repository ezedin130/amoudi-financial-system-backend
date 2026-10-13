package com.amoudi.financial.service;

import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.dto.SalesFilterRequest;
import com.amoudi.financial.dto.SalesTransactionResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

/**
 * Service interface for querying sales transactions and calculating sales totals.
 */
public interface SalesService {

    /**
     * Lists sales transactions with dynamic filtering, pagination, and safe sorting.
     */
    PageResponse<SalesTransactionResponse> listSales(SalesFilterRequest filter, Pageable pageable);

    /**
     * Retrieves an individual sales transaction by ID.
     */
    SalesTransactionResponse getSaleById(Long id);

    /**
     * Calculates total sales for a specific Non-IATA account using database-level aggregation.
     */
    BigDecimal calculateTotalSalesForNonIata(Long nonIataId);
}
