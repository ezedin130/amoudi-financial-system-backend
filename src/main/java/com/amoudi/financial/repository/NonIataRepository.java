package com.amoudi.financial.repository;

import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.model.NonIata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for NonIata entity.
 */
@Repository
public interface NonIataRepository extends JpaRepository<NonIata, Long>, JpaSpecificationExecutor<NonIata> {

    Optional<NonIata> findByCode(String code);

    Optional<NonIata> findByCodeIgnoreCase(String code);

    boolean existsByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<NonIata> findByStatus(NonIataStatus status);

    long countByStatus(NonIataStatus status);

    /**
     * Aggregates total sales per Non-IATA account using database-level GROUP BY.
     * Non-IATAs without sales receive 0.00 via COALESCE.
     */
    @org.springframework.data.jpa.repository.Query(value = """
        SELECT 
            n.id AS nonIataId,
            n.code AS code,
            n.name AS name,
            n.status AS status,
            COALESCE((SELECT SUM(COALESCE(s.grandTotal, s.amount)) FROM SalesTransaction s WHERE s.nonIata = n AND s.currency = 'ETB'), 0.00) AS totalSales,
            COALESCE((SELECT SUM(p.amount) FROM Payment p WHERE p.nonIata = n AND p.status = com.amoudi.financial.enums.PaymentStatus.POSTED AND p.currency = 'ETB'), 0.00) AS totalPayments
        FROM NonIata n
        WHERE (:searchPattern IS NULL OR LOWER(n.code) LIKE :searchPattern OR LOWER(n.name) LIKE :searchPattern)
          AND (:status IS NULL OR n.status = :status)
        """,
        countQuery = """
        SELECT COUNT(n) FROM NonIata n
        WHERE (:searchPattern IS NULL OR LOWER(n.code) LIKE :searchPattern OR LOWER(n.name) LIKE :searchPattern)
          AND (:status IS NULL OR n.status = :status)
        """)
    org.springframework.data.domain.Page<com.amoudi.financial.dto.NonIataFinancialProjection> findFinancialSummaries(
        @org.springframework.data.repository.query.Param("searchPattern") String searchPattern,
        @org.springframework.data.repository.query.Param("status") NonIataStatus status,
        org.springframework.data.domain.Pageable pageable
    );
}
