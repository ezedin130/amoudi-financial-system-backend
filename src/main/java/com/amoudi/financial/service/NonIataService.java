package com.amoudi.financial.service;

import com.amoudi.financial.dto.NonIataCreateRequest;
import com.amoudi.financial.dto.NonIataResponse;
import com.amoudi.financial.dto.NonIataStatusUpdateRequest;
import com.amoudi.financial.dto.NonIataUpdateRequest;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.enums.NonIataStatus;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for managing Non-IATA sub-agent accounts.
 */
public interface NonIataService {

    /**
     * Creates and persists a new Non-IATA account.
     *
     * @param request Creation payload containing unique code and details
     * @return Created NonIataResponse
     */
    NonIataResponse createNonIata(NonIataCreateRequest request);

    /**
     * Retrieves an existing Non-IATA account by its database ID.
     *
     * @param id Database primary key
     * @return NonIataResponse
     */
    NonIataResponse getNonIataById(Long id);

    /**
     * Updates editable details of a Non-IATA account.
     * Note: Code is strictly immutable in V1.
     *
     * @param id Database primary key
     * @param request Update payload
     * @return Updated NonIataResponse
     */
    NonIataResponse updateNonIata(Long id, NonIataUpdateRequest request);

    /**
     * Updates the operational status (ACTIVE / INACTIVE) of a Non-IATA account.
     *
     * @param id Database primary key
     * @param request Status update payload
     * @return Updated NonIataResponse
     */
    NonIataResponse updateStatus(Long id, NonIataStatusUpdateRequest request);

    /**
     * Lists Non-IATA accounts with optional search query, status filtering, and safe pagination/sorting.
     *
     * @param search Optional search term matching code or name
     * @param status Optional NonIataStatus filter
     * @param pageable Pagination and sorting specifications
     * @return Paginated NonIataResponse list
     */
    PageResponse<NonIataResponse> listNonIatas(String search, NonIataStatus status, Pageable pageable);
}
