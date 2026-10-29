package com.amoudi.financial.service;

import com.amoudi.financial.dto.*;
import com.amoudi.financial.enums.AuditAction;
import com.amoudi.financial.enums.ImportErrorType;
import com.amoudi.financial.enums.ImportStatus;
import com.amoudi.financial.enums.TransactionType;
import com.amoudi.financial.exception.BadRequestException;
import com.amoudi.financial.exception.FileValidationException;
import com.amoudi.financial.exception.ResourceNotFoundException;
import com.amoudi.financial.model.*;
import com.amoudi.financial.repository.*;
import com.amoudi.financial.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service implementation orchestrating Galileo/BSP sales report upload, parsing,
 * Non-IATA matching, duplicate prevention, and transactional persistence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportServiceImpl implements ImportService {

    private static final long MAX_FILE_SIZE_BYTES = 25 * 1024 * 1024; // 25MB

    private final FileStorageService fileStorageService;
    private final ExcelParserService excelParserService;
    private final NameMappingService nameMappingService;
    private final ImportBatchRepository importBatchRepository;
    private final ImportBatchErrorRepository importBatchErrorRepository;
    private final SalesTransactionRepository salesTransactionRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public ImportBatchResponse uploadAndProcess(MultipartFile file, Long currentUserId) {
        validateFile(file);

        User uploader = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserId));

        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "report.xlsx";

        log.info("Starting sales report import: filename='{}', size={} bytes, uploadedBy='{}'",
                originalFilename, file.getSize(), uploader.getUsername());

        // 1. Store original file safely in object storage
        String objectKey;
        try {
            objectKey = fileStorageService.store(file, "imports");
        } catch (IOException e) {
            log.error("Failed to store uploaded file: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to store uploaded file: " + e.getMessage());
        }

        // 2. Initialize ImportBatch
        ImportBatch batch = ImportBatch.builder()
                .fileName(originalFilename)
                .objectKey(objectKey)
                .uploadedBy(uploader)
                .uploadedAt(LocalDateTime.now())
                .status(ImportStatus.PROCESSING)
                .totalRows(0)
                .successfulRows(0)
                .duplicateRows(0)
                .unmatchedRows(0)
                .failedRows(0)
                .build();

        batch = importBatchRepository.save(batch);

        // Record Audit Log for uploaded report file
        try {
            AuditLog uploadAudit = AuditLog.builder()
                    .user(uploader)
                    .action(AuditAction.FILE_UPLOAD)
                    .entityType("ImportBatch")
                    .entityId(batch.getId())
                    .newValue(String.format("Uploaded sales report '%s' (objectKey: %s)", originalFilename, objectKey))
                    .build();
            auditLogRepository.save(uploadAudit);
        } catch (Exception e) {
            log.warn("Failed to record upload audit log for batch id {}: {}", batch.getId(), e.getMessage());
        }

        // 3. Parse workbook Sheet2
        ParsedReport report;
        try {
            report = excelParserService.parse(file.getInputStream(), originalFilename);
        } catch (FileValidationException fve) {
            batch.setStatus(ImportStatus.VALIDATION_FAILED);
            batch.setErrorSummary(fve.getMessage());
            importBatchRepository.save(batch);
            throw fve;
        } catch (Exception e) {
            batch.setStatus(ImportStatus.FAILED);
            batch.setErrorSummary("Failed to process workbook: " + e.getMessage());
            importBatchRepository.save(batch);
            throw new BadRequestException("Failed to process workbook: " + e.getMessage());
        }

        // 4. Ingest parsed rows
        List<ImportBatchError> batchErrors = new ArrayList<>();
        List<SalesTransaction> transactionsToSave = new ArrayList<>();
        Set<String> batchSeenKeys = new HashSet<>();

        // Add parsing failures
        for (RowParsingFailure failure : report.getParsingErrors()) {
            batchErrors.add(ImportBatchError.builder()
                    .importBatch(batch)
                    .rowNumber(failure.getRowNumber())
                    .ticketNumber(failure.getTicketNumber())
                    .sourceAgencyName(failure.getSourceAgencyName())
                    .errorType(failure.getErrorType())
                    .message(failure.getMessage())
                    .build());
        }

        int successfulCount = 0;
        int duplicateCount = 0;
        int unmatchedCount = 0;
        int salesCount = 0;
        int refundsCount = 0;
        int exchangesCount = 0;

        for (ParsedSalesRow row : report.getValidRows()) {
            TransactionType txType = row.getTransactionType() != null ? row.getTransactionType() : TransactionType.SALE;

            // Check Non-IATA mapping
            Optional<NonIata> nonIataOpt = nameMappingService.resolveNonIata(row.getSourceAgencyName());
            if (nonIataOpt.isEmpty()) {
                unmatchedCount++;
                batchErrors.add(ImportBatchError.builder()
                        .importBatch(batch)
                        .rowNumber(row.getSourceRowNumber())
                        .ticketNumber(row.getTicketNumber())
                        .sourceAgencyName(row.getSourceAgencyName())
                        .errorType(ImportErrorType.UNMATCHED_AGENCY)
                        .message(String.format("Unmapped source agency name: '%s'", row.getSourceAgencyName()))
                        .build());
                continue;
            }

            // Check duplicate transaction key
            String transKey = row.getSourceTransactionKey();
            boolean isDuplicate = batchSeenKeys.contains(transKey)
                    || salesTransactionRepository.existsBySourceTransactionKey(transKey);

            if (isDuplicate) {
                duplicateCount++;
                batchErrors.add(ImportBatchError.builder()
                        .importBatch(batch)
                        .rowNumber(row.getSourceRowNumber())
                        .ticketNumber(row.getTicketNumber())
                        .sourceAgencyName(row.getSourceAgencyName())
                        .errorType(ImportErrorType.DUPLICATE_TRANSACTION)
                        .message(String.format("Duplicate sales transaction detected (key: %s)", transKey))
                        .build());
                continue;
            }

            batchSeenKeys.add(transKey);

            // Attempt matching refund or exchange to original sale
            SalesTransaction originalSale = null;
            if (txType == TransactionType.REFUND || txType == TransactionType.EXCHANGE) {
                originalSale = findMatchingOriginalSale(nonIataOpt.get(), row, transactionsToSave);
            }

            // Construct immutable SalesTransaction
            BigDecimal grandTotal = row.getGrandTotal() != null ? row.getGrandTotal() : row.getAmount();
            SalesTransaction transaction = SalesTransaction.builder()
                    .nonIata(nonIataOpt.get())
                    .importBatch(batch)
                    .ticketNumber(row.getTicketNumber())
                    .transactionReference(row.getPnr())
                    .saleDate(row.getSaleDate())
                    .passengerName(row.getPassengerName())
                    .origin(row.getOrigin())
                    .destination(row.getDestination())
                    .route(row.getRoute())
                    .baseFare(row.getBaseFare())
                    .tax(row.getTax())
                    .totalSale(row.getTotalSale())
                    .serviceCharge(row.getServiceCharge())
                    .grandTotal(grandTotal)
                    .amount(grandTotal)
                    .currency(row.getCurrency())
                    .sourceAgencyName(row.getSourceAgencyName())
                    .sourceRowNumber(row.getSourceRowNumber())
                    .sourceTransactionKey(transKey)
                    .transactionType(txType)
                    .originalTransaction(originalSale)
                    .build();

            transactionsToSave.add(transaction);
            successfulCount++;
            if (txType == TransactionType.SALE) {
                salesCount++;
            } else if (txType == TransactionType.REFUND) {
                refundsCount++;
            } else if (txType == TransactionType.EXCHANGE) {
                exchangesCount++;
            }
        }

        // 5. Persist valid transactions and errors
        if (!transactionsToSave.isEmpty()) {
            salesTransactionRepository.saveAll(transactionsToSave);
        }
        if (!batchErrors.isEmpty()) {
            importBatchErrorRepository.saveAll(batchErrors);
        }

        // 6. Update batch metrics and lifecycle status
        int totalRows = report.getTotalRowsDetected();
        int failedRows = report.getParsingErrors().size();

        batch.setReportDate(report.getReportDate());
        batch.setTotalRows(totalRows);
        batch.setSuccessfulRows(successfulCount);
        batch.setDuplicateRows(duplicateCount);
        batch.setUnmatchedRows(unmatchedCount);
        batch.setFailedRows(failedRows);
        batch.setSalesCount(salesCount);
        batch.setRefundsCount(refundsCount);
        batch.setExchangesCount(exchangesCount);

        if (totalRows == 0) {
            batch.setStatus(ImportStatus.FAILED);
            batch.setErrorSummary("No transaction rows found in Sheet2");
        } else if (successfulCount == 0) {
            batch.setStatus(ImportStatus.FAILED);
            batch.setErrorSummary(String.format("All %d rows rejected: %d duplicates, %d unmatched, %d errors",
                    totalRows, duplicateCount, unmatchedCount, failedRows));
        } else if (successfulCount < totalRows) {
            batch.setStatus(ImportStatus.PARTIALLY_IMPORTED);
            batch.setErrorSummary(String.format("Imported %d of %d rows (%d duplicates, %d unmatched, %d errors)",
                    successfulCount, totalRows, duplicateCount, unmatchedCount, failedRows));
        } else {
            batch.setStatus(ImportStatus.IMPORTED);
            batch.setErrorSummary(null);
        }

        ImportBatch savedBatch = importBatchRepository.save(batch);

        // 7. Record Audit Log
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(uploader)
                    .action(AuditAction.IMPORT)
                    .entityType("ImportBatch")
                    .entityId(savedBatch.getId())
                    .newValue(String.format("File: %s, Total: %d, Success: %d, Dups: %d, Unmatched: %d, Failed: %d, Status: %s",
                            originalFilename, totalRows, successfulCount, duplicateCount, unmatchedCount, failedRows, savedBatch.getStatus()))
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to record audit log for batch id {}: {}", savedBatch.getId(), e.getMessage());
        }

        log.info("Batch id={} completed with status={}: total={}, success={}, dups={}, unmatched={}, failed={}",
                savedBatch.getId(), savedBatch.getStatus(), totalRows, successfulCount, duplicateCount, unmatchedCount, failedRows);

        return mapToBatchResponse(savedBatch);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ImportBatchResponse> getImportBatches(Pageable pageable) {
        Page<ImportBatch> page = importBatchRepository.findAll(pageable);
        return PageResponse.of(page, this::mapToBatchResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ImportBatchResponse getImportBatchById(Long id) {
        ImportBatch batch = importBatchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ImportBatch", id));
        return mapToBatchResponse(batch);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ImportBatchErrorResponse> getBatchErrors(Long batchId, Pageable pageable) {
        if (!importBatchRepository.existsById(batchId)) {
            throw new ResourceNotFoundException("ImportBatch", batchId);
        }
        Page<ImportBatchError> page = importBatchErrorRepository.findByImportBatchIdOrderByRowNumberAsc(batchId, pageable);
        return PageResponse.of(page, this::mapToErrorResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImportBatchErrorResponse> getAllBatchErrors(Long batchId) {
        if (!importBatchRepository.existsById(batchId)) {
            throw new ResourceNotFoundException("ImportBatch", batchId);
        }
        return importBatchErrorRepository.findByImportBatchIdOrderByRowNumberAsc(batchId).stream()
                .map(this::mapToErrorResponse)
                .toList();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileValidationException("Uploaded file cannot be null or empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new FileValidationException(
                    String.format("File size exceeds maximum allowed limit of %d MB", MAX_FILE_SIZE_BYTES / (1024 * 1024)));
        }

        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw new FileValidationException("File must have a valid filename");
        }

        String lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new FileValidationException("Invalid file format. Only Excel spreadsheets (.xlsx, .xls) are accepted");
        }
    }

    private ImportBatchResponse mapToBatchResponse(ImportBatch batch) {
        return ImportBatchResponse.builder()
                .id(batch.getId())
                .fileName(batch.getFileName())
                .objectKey(batch.getObjectKey())
                .reportDate(batch.getReportDate())
                .uploadedById(batch.getUploadedBy() != null ? batch.getUploadedBy().getId() : null)
                .uploadedByUsername(batch.getUploadedBy() != null ? batch.getUploadedBy().getUsername() : null)
                .uploadedAt(batch.getUploadedAt())
                .totalRows(batch.getTotalRows())
                .successfulRows(batch.getSuccessfulRows())
                .duplicateRows(batch.getDuplicateRows())
                .unmatchedRows(batch.getUnmatchedRows())
                .failedRows(batch.getFailedRows())
                .salesCount(batch.getSalesCount())
                .refundsCount(batch.getRefundsCount())
                .exchangesCount(batch.getExchangesCount())
                .status(batch.getStatus())
                .errorSummary(batch.getErrorSummary())
                .createdAt(batch.getCreatedAt())
                .build();
    }

    private SalesTransaction findMatchingOriginalSale(NonIata nonIata, ParsedSalesRow row, List<SalesTransaction> currentBatchTransactions) {
        String ticketToMatch = row.getTicketNumber();
        if (!StringUtils.hasText(ticketToMatch)) {
            return null;
        }

        String cleanTargetTicket = ticketToMatch.replaceAll("\\s+", "").trim();
        if (cleanTargetTicket.isEmpty()) {
            return null;
        }

        List<SalesTransaction> candidates = new ArrayList<>();

        // 1. Search current in-flight batch transactions
        for (SalesTransaction st : currentBatchTransactions) {
            if (st.getNonIata().getId().equals(nonIata.getId())
                    && st.getTicketNumber() != null
                    && cleanTargetTicket.equalsIgnoreCase(st.getTicketNumber().replaceAll("\\s+", "").trim())) {
                candidates.add(st);
            }
        }

        // 2. Search already persisted database transactions
        List<SalesTransaction> dbSales = salesTransactionRepository.findByNonIataId(nonIata.getId());
        for (SalesTransaction st : dbSales) {
            if (st.getTicketNumber() != null
                    && cleanTargetTicket.equalsIgnoreCase(st.getTicketNumber().replaceAll("\\s+", "").trim())) {
                candidates.add(st);
            }
        }

        if (candidates.isEmpty()) {
            log.debug("No candidate original sale found for ticket '{}' on Non-IATA {}", ticketToMatch, nonIata.getCode());
            return null;
        }

        if (candidates.size() == 1) {
            SalesTransaction candidate = candidates.get(0);
            // Verify passenger name if both are present
            if (StringUtils.hasText(row.getPassengerName()) && StringUtils.hasText(candidate.getPassengerName())) {
                String p1 = normalizePassengerName(row.getPassengerName());
                String p2 = normalizePassengerName(candidate.getPassengerName());
                if (!p1.equals(p2) && !p1.contains(p2) && !p2.contains(p1)) {
                    log.warn("Conflicting passenger names between {} row ('{}') and candidate sale ('{}') on ticket {}",
                            row.getTransactionType(), row.getPassengerName(), candidate.getPassengerName(), ticketToMatch);
                    return null; // Evidence conflicts
                }
            }
            return candidate;
        }

        // Multiple candidates exist -> ambiguous evidence, require review instead of guessing
        log.warn("Multiple candidate sales ({}) found for ticket '{}' on Non-IATA {}",
                candidates.size(), ticketToMatch, nonIata.getCode());
        return null;
    }

    private String normalizePassengerName(String name) {
        if (name == null) return "";
        return name.replaceAll("[^a-zA-Z]", "").toLowerCase(Locale.ROOT);
    }

    private ImportBatchErrorResponse mapToErrorResponse(ImportBatchError err) {
        return ImportBatchErrorResponse.builder()
                .id(err.getId())
                .importBatchId(err.getImportBatch().getId())
                .rowNumber(err.getRowNumber())
                .ticketNumber(err.getTicketNumber())
                .sourceAgencyName(err.getSourceAgencyName())
                .errorType(err.getErrorType())
                .message(err.getMessage())
                .createdAt(err.getCreatedAt())
                .build();
    }
}
