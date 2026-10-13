package com.amoudi.financial.controller;

import com.amoudi.financial.dto.AgingReportResponse;
import com.amoudi.financial.dto.ApiResponse;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.service.AgingService;
import com.amoudi.financial.service.ReportExportService;
import com.amoudi.financial.utils.Constants;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST controller for Accounts Receivable aging reports and exports (Excel, PDF).
 */
@RestController
@RequestMapping(Constants.AGING_API_PREFIX)
@PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
public class AgingController {

    private final AgingService agingService;
    private final ReportExportService reportExportService;

    public AgingController(AgingService agingService, ReportExportService reportExportService) {
        this.agingService = agingService;
        this.reportExportService = reportExportService;
    }

    /**
     * Retrieves an aging report as of a given date with optional filtering.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AgingReportResponse>> getAgingReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) Long nonIataId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) NonIataStatus status
    ) {
        AgingReportResponse response = agingService.getAgingReport(asOfDate, nonIataId, search, status);
        return ResponseEntity.ok(ApiResponse.ok("Aging report generated successfully", response));
    }

    /**
     * Exports the aging report to Excel format.
     */
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportAgingExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) Long nonIataId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) NonIataStatus status
    ) {
        AgingReportResponse agingReport = agingService.getAgingReport(asOfDate, nonIataId, search, status);
        byte[] excelBytes = reportExportService.exportAgingToExcel(agingReport);

        String filename = String.format("aging_report_%s.xlsx", agingReport.getAsOfDate());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    /**
     * Exports the aging report to PDF format.
     */
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportAgingPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) Long nonIataId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) NonIataStatus status
    ) {
        AgingReportResponse agingReport = agingService.getAgingReport(asOfDate, nonIataId, search, status);
        byte[] pdfBytes = reportExportService.exportAgingToPdf(agingReport);

        String filename = String.format("aging_report_%s.pdf", agingReport.getAsOfDate());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
