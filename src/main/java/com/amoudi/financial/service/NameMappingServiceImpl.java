package com.amoudi.financial.service;

import com.amoudi.financial.dto.NameMappingRequest;
import com.amoudi.financial.dto.NameMappingResponse;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.exception.DuplicateResourceException;
import com.amoudi.financial.exception.ResourceNotFoundException;
import com.amoudi.financial.model.NameMapping;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.NameMappingRepository;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Service implementation managing source agency name mappings and resolving Non-IATA accounts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NameMappingServiceImpl implements NameMappingService {

    private final NameMappingRepository nameMappingRepository;
    private final NonIataRepository nonIataRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<NonIata> resolveNonIata(String sourceAgencyName) {
        if (!StringUtils.hasText(sourceAgencyName)) {
            return Optional.empty();
        }
        String normalized = sourceAgencyName.trim();
        return nameMappingRepository.findBySourceNameIgnoreCase(normalized)
                .map(NameMapping::getNonIata);
    }

    @Override
    @Transactional
    public NameMappingResponse createMapping(NameMappingRequest request, Long currentUserId) {
        String cleanSource = request.getSourceName().trim();

        if (nameMappingRepository.existsBySourceNameIgnoreCase(cleanSource)) {
            throw new DuplicateResourceException("NameMapping", "sourceName", cleanSource);
        }

        NonIata nonIata = nonIataRepository.findById(request.getNonIataId())
                .orElseThrow(() -> new ResourceNotFoundException("NonIata", request.getNonIataId()));

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserId));

        NameMapping mapping = NameMapping.builder()
                .sourceName(cleanSource)
                .nonIata(nonIata)
                .createdBy(user)
                .build();

        NameMapping saved = nameMappingRepository.save(mapping);
        log.info("Registered name mapping id={}: '{}' -> Non-IATA '{}' ({}) by user '{}'",
                saved.getId(), cleanSource, nonIata.getName(), nonIata.getCode(), user.getUsername());

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void ensureMappingsForNonIata(NonIata nonIata, User creatorUser) {
        if (nonIata == null) {
            return;
        }

        User user = creatorUser;
        if (user == null) {
            user = userRepository.findByUsername("admin")
                    .or(() -> userRepository.findAll().stream().findFirst())
                    .orElse(null);
        }

        // 1. Authoritative code mapping (primary key for sales import matching)
        if (StringUtils.hasText(nonIata.getCode())) {
            ensureSingleMapping(nonIata.getCode().trim().toUpperCase(), nonIata, user);
        }

        // 2. Canonical agency name mapping (if distinct from code)
        if (StringUtils.hasText(nonIata.getName())
                && !nonIata.getName().trim().equalsIgnoreCase(nonIata.getCode().trim())) {
            ensureSingleMapping(nonIata.getName().trim(), nonIata, user);
        }
    }

    private void ensureSingleMapping(String sourceName, NonIata nonIata, User user) {
        String cleanSource = sourceName.trim();
        if (nameMappingRepository.existsBySourceNameIgnoreCase(cleanSource)) {
            log.debug("Mapping for source name '{}' already exists. Skipping auto-registration.", cleanSource);
            return;
        }

        NameMapping mapping = NameMapping.builder()
                .sourceName(cleanSource)
                .nonIata(nonIata)
                .createdBy(user)
                .build();

        NameMapping saved = nameMappingRepository.save(mapping);
        log.info("Auto-registered name mapping id={}: '{}' -> Non-IATA '{}' ({})",
                saved.getId(), cleanSource, nonIata.getName(), nonIata.getCode());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NameMappingResponse> getAllMappings() {
        return nameMappingRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NameMappingResponse> getMappings(Pageable pageable) {
        Page<NameMapping> page = nameMappingRepository.findAll(pageable);
        return PageResponse.of(page, this::mapToResponse);
    }

    private NameMappingResponse mapToResponse(NameMapping mapping) {
        return NameMappingResponse.builder()
                .id(mapping.getId())
                .sourceName(mapping.getSourceName())
                .nonIataId(mapping.getNonIata().getId())
                .nonIataCode(mapping.getNonIata().getCode())
                .nonIataName(mapping.getNonIata().getName())
                .createdById(mapping.getCreatedBy() != null ? mapping.getCreatedBy().getId() : null)
                .createdByUsername(mapping.getCreatedBy() != null ? mapping.getCreatedBy().getUsername() : null)
                .createdAt(mapping.getCreatedAt())
                .build();
    }
}
