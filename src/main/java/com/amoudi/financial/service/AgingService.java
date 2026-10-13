package com.amoudi.financial.service;

import com.amoudi.financial.dto.AgingReportResponse;
import com.amoudi.financial.enums.NonIataStatus;

import java.time.LocalDate;

/**
 * Service for calculating Accounts Receivable aging reports based on FIFO payment application.
 */
public interface AgingService {

    /**
     * Calculates an aging report as of a specified date with optional filters.
     *
     * @param asOfDate  effective date for calculating aging buckets (defaults to today if null)
     * @param nonIataId optional filter by specific Non-IATA account ID
     * @param search    optional text filter across code and name
     * @param status    optional filter by Non-IATA status
     * @return the calculated aging report
     */
    AgingReportResponse getAgingReport(LocalDate asOfDate, Long nonIataId, String search, NonIataStatus status);
}
