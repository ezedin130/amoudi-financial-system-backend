package com.amoudi.financial.controller;

import com.amoudi.financial.dto.ApiResponse;
import com.amoudi.financial.dto.ImportBatchErrorResponse;
import com.amoudi.financial.dto.ImportBatchResponse;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.model.UserPrincipal;
import com.amoudi.financial.service.ImportService;
import com.amoudi.financial.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for Galileo/BSP sales report ingestion and batch inspection.
 *
 * Authorization rules:
 * - Upload/process: strictly restricted to FINANCE role (GENERAL_MANAGER -> 403 Forbidden)
 * - View batches and errors: accessible by FINANCE and GENERAL_MANAGER
 */
@RestController
@RequestMapping(Constants.IMPORTS_API_PREFIX)
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    /**
     * Ingests a Galileo/BSP daily sales Excel report.
     * Enforces Sheet2 authority, G.TOT amounts, duplicate prevention, and Non-IATA mapping.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<ImportBatchResponse>> uploadAndProcessReport(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ImportBatchResponse response = importService.uploadAndProcess(file, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Sales report uploaded and processed successfully", response));
    }

    /**
     * Lists paginated import batch history.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<ImportBatchResponse>>> listImportBatches(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<ImportBatchResponse> page = importService.getImportBatches(pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    /**
     * Retrieves import batch details by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<ImportBatchResponse>> getImportBatchById(@PathVariable Long id) {
        ImportBatchResponse response = importService.getImportBatchById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Retrieves paginated row-level rejection/unmatched errors for a specific import batch.
     */
    @GetMapping("/{id}/errors")
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<ImportBatchErrorResponse>>> getBatchErrors(
            @PathVariable Long id,
            @PageableDefault(size = 50, sort = "rowNumber", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<ImportBatchErrorResponse> errors = importService.getBatchErrors(id, pageable);
        return ResponseEntity.ok(ApiResponse.ok(errors));
    }
}
