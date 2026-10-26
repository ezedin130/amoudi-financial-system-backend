package com.amoudi.financial.controller;

import com.amoudi.financial.dto.ApiResponse;
import com.amoudi.financial.dto.AuditLogResponse;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.repository.AuditLogRepository;
import com.amoudi.financial.utils.Constants;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for querying immutable audit logs.
 * Accessible by FINANCE and GENERAL_MANAGER roles.
 */
@RestController
@RequestMapping(Constants.AUDIT_LOGS_API_PREFIX)
@PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ApiResponse<PageResponse<AuditLogResponse>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogResponse> responsePage = auditLogRepository.findAll(pageable)
                .map(AuditLogResponse::fromEntity);
        return ApiResponse.ok(PageResponse.of(responsePage));
    }
}
