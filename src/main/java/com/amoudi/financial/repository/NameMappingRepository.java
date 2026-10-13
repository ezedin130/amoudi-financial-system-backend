package com.amoudi.financial.repository;

import com.amoudi.financial.model.NameMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for NameMapping entity.
 */
@Repository
public interface NameMappingRepository extends JpaRepository<NameMapping, Long> {

    Optional<NameMapping> findBySourceName(String sourceName);

    Optional<NameMapping> findBySourceNameIgnoreCase(String sourceName);

    boolean existsBySourceName(String sourceName);

    boolean existsBySourceNameIgnoreCase(String sourceName);
}
