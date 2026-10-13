package com.amoudi.financial.controller;

import com.amoudi.financial.dto.AccountStatementResponse;
import com.amoudi.financial.dto.ApiResponse;
import com.amoudi.financial.service.ReportExportService;
import com.amoudi.financial.service.StatementService;
import com.amoudi.financial.utils.Constants;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST controller for Non-IATA account statements and statement exports (Excel, PDF).
 */
@RestController
@RequestMapping(Constants.NON_IATAS_API_PREFIX + "/{nonIataId}/statement")
@PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
public class StatementController {

    private final StatementService statementService;
    private final ReportExportService reportExportService;

    public StatementController(StatementService statementService, ReportExportService reportExportService) {
        this.statementService = statementService;
        this.reportExportService = reportExportService;
    }

    /**
     * Retrieves an account statement for the specified Non-IATA with optional date filtering.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AccountStatementResponse>> getStatement(
            @PathVariable Long nonIataId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        AccountStatementResponse response = statementService.getStatement(nonIataId, dateFrom, dateTo);
        return ResponseEntity.ok(ApiResponse.ok("Statement generated successfully", response));
    }

    /**
     * Exports the account statement to Excel format.
     */
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportStatementExcel(
            @PathVariable Long nonIataId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        AccountStatementResponse statement = statementService.getStatement(nonIataId, dateFrom, dateTo);
        byte[] excelBytes = reportExportService.exportStatementToExcel(statement);

        String filename = String.format("statement_%s_%s.xlsx",
                statement.getNonIataCode(),
                LocalDate.now());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    /**
     * Exports the account statement to PDF format.
     */
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportStatementPdf(
            @PathVariable Long nonIataId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        AccountStatementResponse statement = statementService.getStatement(nonIataId, dateFrom, dateTo);
        byte[] pdfBytes = reportExportService.exportStatementToPdf(statement);

        String filename = String.format("statement_%s_%s.pdf",
                statement.getNonIataCode(),
                LocalDate.now());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
