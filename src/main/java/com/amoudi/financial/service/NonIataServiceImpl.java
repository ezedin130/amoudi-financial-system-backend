package com.amoudi.financial.service;

import com.amoudi.financial.dto.NonIataCreateRequest;
import com.amoudi.financial.dto.NonIataResponse;
import com.amoudi.financial.dto.NonIataStatusUpdateRequest;
import com.amoudi.financial.dto.NonIataUpdateRequest;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.exception.BadRequestException;
import com.amoudi.financial.exception.DuplicateResourceException;
import com.amoudi.financial.exception.ResourceNotFoundException;
import com.amoudi.financial.model.AuditLog;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.User;
import com.amoudi.financial.model.UserPrincipal;
import com.amoudi.financial.repository.AuditLogRepository;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.NonIataSpecification;
import com.amoudi.financial.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service implementation for Non-IATA sub-agent management.
 */
@Service
@Transactional(readOnly = true)
public class NonIataServiceImpl implements NonIataService {

    private static final Logger log = LoggerFactory.getLogger(NonIataServiceImpl.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id",
            "code",
            "name",
            "status",
            "contactName",
            "phone",
            "email",
            "createdAt",
            "updatedAt"
    );

    private final NonIataRepository nonIataRepository;
    private final AuditLogRepository auditLogRepository;
    private final NameMappingService nameMappingService;
    private final UserRepository userRepository;

    public NonIataServiceImpl(
            NonIataRepository nonIataRepository,
            AuditLogRepository auditLogRepository,
            NameMappingService nameMappingService,
            UserRepository userRepository
    ) {
        this.nonIataRepository = nonIataRepository;
        this.auditLogRepository = auditLogRepository;
        this.nameMappingService = nameMappingService;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public NonIataResponse createNonIata(NonIataCreateRequest request) {
        String normalizedCode = request.getCode().trim().toUpperCase();
        String normalizedName = request.getName().trim();

        if (nonIataRepository.existsByCodeIgnoreCase(normalizedCode)) {
            log.warn("Attempted to create duplicate Non-IATA with code: {}", normalizedCode);
            throw new DuplicateResourceException("NonIata", "code", normalizedCode);
        }

        NonIata nonIata = NonIata.builder()
                .code(normalizedCode)
                .name(normalizedName)
                .status(NonIataStatus.ACTIVE)
                .contactName(trimToNull(request.getContactName()))
                .phone(trimToNull(request.getPhone()))
                .email(normalizeEmail(request.getEmail()))
                .notes(trimToNull(request.getNotes()))
                .build();

        NonIata saved = nonIataRepository.save(nonIata);
        log.info("Created Non-IATA account: code={}, id={}", saved.getCode(), saved.getId());

        // Resolve authenticated user for audit trail and mapping attribution
        User currentUser = resolveCurrentUser();

        // Automatically ensure authoritative name mappings derived from code and name
        nameMappingService.ensureMappingsForNonIata(saved, currentUser);

        // Record Audit Log
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(currentUser)
                    .action(com.amoudi.financial.enums.AuditAction.CREATE)
                    .entityType("NonIata")
                    .entityId(saved.getId())
                    .newValue(String.format("Created Non-IATA code=%s, name=%s", saved.getCode(), saved.getName()))
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to record audit log for Non-IATA creation id {}: {}", saved.getId(), e.getMessage());
        }

        return NonIataResponse.fromEntity(saved);
    }

    @Override
    public NonIataResponse getNonIataById(Long id) {
        NonIata nonIata = nonIataRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NonIata", id));
        return NonIataResponse.fromEntity(nonIata);
    }

    @Override
    @Transactional
    public NonIataResponse updateNonIata(Long id, NonIataUpdateRequest request) {
        NonIata nonIata = nonIataRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NonIata", id));

        String oldSnapshot = String.format("name=%s, contact=%s, phone=%s, email=%s",
                nonIata.getName(), nonIata.getContactName(), nonIata.getPhone(), nonIata.getEmail());

        nonIata.setName(request.getName().trim());
        nonIata.setContactName(trimToNull(request.getContactName()));
        nonIata.setPhone(trimToNull(request.getPhone()));
        nonIata.setEmail(normalizeEmail(request.getEmail()));
        nonIata.setNotes(trimToNull(request.getNotes()));

        NonIata updated = nonIataRepository.save(nonIata);
        log.info("Updated Non-IATA account: id={}, code={}", updated.getId(), updated.getCode());

        User currentUser = resolveCurrentUser();
        // Ensure mapping for any newly changed agency name (preserving immutable code mapping)
        nameMappingService.ensureMappingsForNonIata(updated, currentUser);

        // Record Audit Log
        try {
            String newSnapshot = String.format("name=%s, contact=%s, phone=%s, email=%s",
                    updated.getName(), updated.getContactName(), updated.getPhone(), updated.getEmail());
            AuditLog auditLog = AuditLog.builder()
                    .user(currentUser)
                    .action(com.amoudi.financial.enums.AuditAction.UPDATE)
                    .entityType("NonIata")
                    .entityId(updated.getId())
                    .oldValue(oldSnapshot)
                    .newValue(newSnapshot)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to record audit log for Non-IATA update id {}: {}", updated.getId(), e.getMessage());
        }

        return NonIataResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public NonIataResponse updateStatus(Long id, NonIataStatusUpdateRequest request) {
        NonIata nonIata = nonIataRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NonIata", id));

        NonIataStatus previousStatus = nonIata.getStatus();
        nonIata.setStatus(request.getStatus());

        NonIata updated = nonIataRepository.save(nonIata);
        log.info("Updated status for Non-IATA id={} from {} to {}", id, previousStatus, updated.getStatus());

        User currentUser = resolveCurrentUser();

        // Record Audit Log
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(currentUser)
                    .action(com.amoudi.financial.enums.AuditAction.UPDATE)
                    .entityType("NonIata")
                    .entityId(updated.getId())
                    .oldValue("status: " + previousStatus)
                    .newValue("status: " + updated.getStatus())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to record audit log for Non-IATA status update id {}: {}", updated.getId(), e.getMessage());
        }

        return NonIataResponse.fromEntity(updated);
    }

    @Override
    public PageResponse<NonIataResponse> listNonIatas(String search, NonIataStatus status, Pageable pageable) {
        Pageable safePageable = sanitizePageable(pageable);
        Specification<NonIata> spec = NonIataSpecification.withFilters(search, status);
        Page<NonIata> page = nonIataRepository.findAll(spec, safePageable);
        return PageResponse.of(page, NonIataResponse::fromEntity);
    }

    /**
     * Sanitizes Pageable input to prevent unbound result sets and SQL injection via order by.
     */
    private Pageable sanitizePageable(Pageable pageable) {
        int pageNumber = Math.max(0, pageable.getPageNumber());
        int pageSize = pageable.getPageSize() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        } else {
            List<Sort.Order> safeOrders = new ArrayList<>();
            for (Sort.Order order : sort) {
                if (ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                    safeOrders.add(order);
                } else {
                    log.warn("Rejected invalid sort property: {}", order.getProperty());
                    throw new BadRequestException("Invalid sort property: '" + order.getProperty()
                            + "'. Allowed properties: " + ALLOWED_SORT_PROPERTIES);
                }
            }
            sort = Sort.by(safeOrders);
        }

        return PageRequest.of(pageNumber, pageSize, sort);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    /**
     * Resolves the authenticated user from the Spring SecurityContextHolder.
     * Falls back to the admin user or first existing user for unauthenticated or system invocations.
     */
    private User resolveCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                if (auth.getPrincipal() instanceof UserPrincipal up && up.getId() != null) {
                    Optional<User> userOpt = userRepository.findById(up.getId());
                    if (userOpt.isPresent()) {
                        return userOpt.get();
                    }
                }
                if (StringUtils.hasText(auth.getName()) && !"anonymousUser".equals(auth.getName())) {
                    Optional<User> userOpt = userRepository.findByUsername(auth.getName());
                    if (userOpt.isPresent()) {
                        return userOpt.get();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve authenticated user from security context: {}", e.getMessage());
        }

        return userRepository.findByUsername("admin")
                .or(() -> userRepository.findAll().stream().findFirst())
                .orElse(null);
    }
}
