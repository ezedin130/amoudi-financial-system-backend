package com.amoudi.financial.repository;

import com.amoudi.financial.enums.AuditAction;
import com.amoudi.financial.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for AuditLog entity.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUserId(Long userId);

    List<AuditLog> findByAction(AuditAction action);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user"})
    org.springframework.data.domain.Page<AuditLog> findAll(org.springframework.data.domain.Pageable pageable);

    List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);
}
