package com.amoudi.financial.service;

import com.amoudi.financial.dto.NameMappingRequest;
import com.amoudi.financial.dto.NameMappingResponse;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.User;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Service contract for managing and resolving source agency name mappings to Non-IATA accounts.
 */
public interface NameMappingService {

    /**
     * Resolves the canonical Non-IATA account from a source agency text.
     * Uses whitespace and case normalization. Never performs unsafe fuzzy guessing.
     *
     * @param sourceAgencyName raw name from Excel Form Of Payment Free Text
     * @return Optional containing NonIata if mapped, or empty if unknown
     */
    Optional<NonIata> resolveNonIata(String sourceAgencyName);

    /**
     * Registers a new authoritative mapping from a source name to a Non-IATA account.
     *
     * @param request creation request
     * @param currentUserId ID of authenticated user creating the mapping
     * @return created NameMappingResponse
     */
    NameMappingResponse createMapping(NameMappingRequest request, Long currentUserId);

    /**
     * Ensures authoritative name mappings exist for the given Non-IATA account.
     * Automatically derives and registers mappings from the account's unique code
     * and canonical name, ensuring the sub-agent is immediately resolvable during
     * Excel import.
     *
     * This operation is idempotent and does not fail if a mapping already exists.
     *
     * @param nonIata the newly created or updated Non-IATA entity
     * @param creatorUser the user responsible for creating the mapping (optional)
     */
    void ensureMappingsForNonIata(NonIata nonIata, User creatorUser);

    /**
     * Retrieves all registered name mappings.
     *
     * @return list of name mappings
     */
    List<NameMappingResponse> getAllMappings();

    /**
     * Retrieves paginated name mappings.
     *
     * @param pageable pagination parameters
     * @return PageResponse of NameMappingResponse
     */
    PageResponse<NameMappingResponse> getMappings(Pageable pageable);
}
