package com.amoudi.financial.controller;

import com.amoudi.financial.dto.ApiResponse;
import com.amoudi.financial.dto.NameMappingRequest;
import com.amoudi.financial.dto.NameMappingResponse;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.model.UserPrincipal;
import com.amoudi.financial.service.NameMappingService;
import com.amoudi.financial.utils.Constants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing source agency name mappings to canonical Non-IATA accounts.
 *
 * Authorization rules:
 * - Read endpoints: accessible by FINANCE and GENERAL_MANAGER
 * - Create endpoint: restricted to FINANCE
 */
@RestController
@RequestMapping(Constants.NAME_MAPPINGS_API_PREFIX)
@RequiredArgsConstructor
public class NameMappingController {

    private final NameMappingService nameMappingService;

    /**
     * Lists paginated name mappings.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<NameMappingResponse>>> listMappings(
            @PageableDefault(size = 50, sort = "sourceName", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<NameMappingResponse> page = nameMappingService.getMappings(pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    /**
     * Registers a new authoritative source name to Non-IATA mapping.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<NameMappingResponse>> createMapping(
            @Valid @RequestBody NameMappingRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        NameMappingResponse response = nameMappingService.createMapping(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Name mapping registered successfully", response));
    }
}
