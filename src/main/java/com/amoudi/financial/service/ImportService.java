package com.amoudi.financial.service;

import com.amoudi.financial.dto.ImportBatchErrorResponse;
import com.amoudi.financial.dto.ImportBatchResponse;
import com.amoudi.financial.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Service contract for Galileo/BSP report ingestion, processing, batch lifecycle, and query.
 */
public interface ImportService {

    /**
     * Stores, parses, validates, and ingests a Galileo/BSP sales report workbook.
     * Enforces Sheet2 authority, G.TOT financial rule, duplicate detection, and Non-IATA mapping.
     *
     * @param file uploaded Excel workbook
     * @param currentUserId ID of authenticated user performing the upload
     * @return ImportBatchResponse with full ingestion metrics
     */
    ImportBatchResponse uploadAndProcess(MultipartFile file, Long currentUserId);

    /**
     * Retrieves paginated import batch history.
     *
     * @param pageable pagination parameters
     * @return PageResponse of ImportBatchResponse
     */
    PageResponse<ImportBatchResponse> getImportBatches(Pageable pageable);

    /**
     * Retrieves details of a specific import batch.
     *
     * @param id batch ID
     * @return ImportBatchResponse
     */
    ImportBatchResponse getImportBatchById(Long id);

    /**
     * Retrieves paginated row-level errors for a specific import batch.
     *
     * @param batchId batch ID
     * @param pageable pagination parameters
     * @return PageResponse of ImportBatchErrorResponse
     */
    PageResponse<ImportBatchErrorResponse> getBatchErrors(Long batchId, Pageable pageable);

    /**
     * Retrieves all row-level errors for a specific import batch.
     *
     * @param batchId batch ID
     * @return list of ImportBatchErrorResponse
     */
    List<ImportBatchErrorResponse> getAllBatchErrors(Long batchId);
}
