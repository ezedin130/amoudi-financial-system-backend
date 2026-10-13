package com.amoudi.financial.service;

import com.amoudi.financial.dto.AccountStatementResponse;
import com.amoudi.financial.dto.AgingReportResponse;

/**
 * Service for generating Excel (.xlsx) and PDF (.pdf) exports for statements and aging reports.
 */
public interface ReportExportService {

    /**
     * Exports an account statement to Excel format.
     *
     * @param statement the statement data
     * @return binary Excel document bytes (.xlsx)
     */
    byte[] exportStatementToExcel(AccountStatementResponse statement);

    /**
     * Exports an aging report to Excel format.
     *
     * @param agingReport the aging report data
     * @return binary Excel document bytes (.xlsx)
     */
    byte[] exportAgingToExcel(AgingReportResponse agingReport);

    /**
     * Exports an account statement to PDF format.
     *
     * @param statement the statement data
     * @return binary PDF document bytes (.pdf)
     */
    byte[] exportStatementToPdf(AccountStatementResponse statement);

    /**
     * Exports an aging report to PDF format.
     *
     * @param agingReport the aging report data
     * @return binary PDF document bytes (.pdf)
     */
    byte[] exportAgingToPdf(AgingReportResponse agingReport);
}
