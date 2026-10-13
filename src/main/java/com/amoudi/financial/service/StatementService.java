package com.amoudi.financial.service;

import com.amoudi.financial.dto.AccountStatementResponse;

import java.time.LocalDate;

/**
 * Service for generating chronological account statements for Non-IATA accounts.
 */
public interface StatementService {

    /**
     * Generates an account statement for a Non-IATA account within an optional date range.
     *
     * @param nonIataId the Non-IATA account ID
     * @param dateFrom   optional start date (inclusive)
     * @param dateTo     optional end date (inclusive)
     * @return the generated account statement
     */
    AccountStatementResponse getStatement(Long nonIataId, LocalDate dateFrom, LocalDate dateTo);
}
