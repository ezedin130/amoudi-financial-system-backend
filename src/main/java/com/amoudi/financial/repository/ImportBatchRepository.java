package com.amoudi.financial.repository;

import com.amoudi.financial.enums.ImportStatus;
import com.amoudi.financial.model.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for ImportBatch entity.
 */
@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    Optional<ImportBatch> findByObjectKey(String objectKey);

    List<ImportBatch> findByStatus(ImportStatus status);
}
