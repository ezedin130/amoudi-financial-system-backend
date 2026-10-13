package com.amoudi.financial.repository;

import com.amoudi.financial.model.ImportBatchError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for ImportBatchError entity.
 */
@Repository
public interface ImportBatchErrorRepository extends JpaRepository<ImportBatchError, Long> {

    List<ImportBatchError> findByImportBatchIdOrderByRowNumberAsc(Long importBatchId);

    Page<ImportBatchError> findByImportBatchIdOrderByRowNumberAsc(Long importBatchId, Pageable pageable);

    long countByImportBatchId(Long importBatchId);
}
