package com.amoudi.financial.repository;

import com.amoudi.financial.model.SystemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for SystemStatus.
 */
@Repository
public interface SystemStatusRepository extends JpaRepository<SystemStatus, UUID> {

    /**
     * Finds a status record by its unique status name.
     *
     * @param statusName the status identifier
     * @return an Optional containing the SystemStatus if present
     */
    Optional<SystemStatus> findByStatusName(String statusName);
}
