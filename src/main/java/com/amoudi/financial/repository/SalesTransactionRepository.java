package com.amoudi.financial.repository;

import com.amoudi.financial.model.SalesTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for SalesTransaction entity.
 */
@Repository
public interface SalesTransactionRepository extends JpaRepository<SalesTransaction, Long>, JpaSpecificationExecutor<SalesTransaction> {

    @Override
    @EntityGraph(attributePaths = {"nonIata", "importBatch"})
    Page<SalesTransaction> findAll(Specification<SalesTransaction> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"nonIata", "importBatch"})
    Optional<SalesTransaction> findById(Long id);

    List<SalesTransaction> findByNonIataId(Long nonIataId);

    List<SalesTransaction> findByImportBatchId(Long importBatchId);

    boolean existsBySourceTransactionKey(String sourceTransactionKey);

    Optional<SalesTransaction> findBySourceTransactionKey(String sourceTransactionKey);

    List<SalesTransaction> findBySaleDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Calculates the sum of sales amounts for a Non-IATA account in a given currency.
     * Returns 0.00 if no sales exist.
     */
    @Query("SELECT COALESCE(SUM(COALESCE(s.grandTotal, s.amount)), 0.00) FROM SalesTransaction s WHERE s.nonIata.id = :nonIataId AND s.currency = :currency")
    BigDecimal sumAmountByNonIataIdAndCurrency(@Param("nonIataId") Long nonIataId, @Param("currency") String currency);

    /**
     * Calculates the company-wide sum of all sales amounts (Grand Total) in a given currency.
     */
    @Query("SELECT COALESCE(SUM(COALESCE(s.grandTotal, s.amount)), 0.00) FROM SalesTransaction s WHERE s.currency = :currency")
    BigDecimal sumTotalSalesByCurrency(@Param("currency") String currency);

    /**
     * Retrieves all distinct currencies used in sales transactions for a Non-IATA account.
     * Used for multi-currency safety validation.
     */
    @Query("SELECT DISTINCT s.currency FROM SalesTransaction s WHERE s.nonIata.id = :nonIataId")
    List<String> findDistinctCurrenciesByNonIataId(@Param("nonIataId") Long nonIataId);

    /**
     * Retrieves sales transactions for a Non-IATA within an optional date range in deterministic order.
     */
    @Query("SELECT s FROM SalesTransaction s WHERE s.nonIata.id = :nonIataId " +
           "  AND (:dateFrom IS NULL OR s.saleDate >= :dateFrom) " +
           "  AND (:dateTo IS NULL OR s.saleDate <= :dateTo) " +
           "ORDER BY s.saleDate ASC, s.id ASC")
    List<SalesTransaction> findStatementSales(
            @Param("nonIataId") Long nonIataId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );

    /**
     * Calculates the sum of sales amounts for a Non-IATA before a given date (for opening balance).
     */
    @Query("SELECT COALESCE(SUM(COALESCE(s.grandTotal, s.amount)), 0.00) FROM SalesTransaction s " +
           "WHERE s.nonIata.id = :nonIataId " +
           "  AND s.saleDate < :dateFrom " +
           "  AND s.currency = :currency")
    BigDecimal sumSalesBeforeDate(
            @Param("nonIataId") Long nonIataId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("currency") String currency
    );

    /**
     * Calculates the sum of sales amounts for a Non-IATA within an optional date range.
     */
    @Query("SELECT COALESCE(SUM(COALESCE(s.grandTotal, s.amount)), 0.00) FROM SalesTransaction s " +
           "WHERE s.nonIata.id = :nonIataId " +
           "  AND (:dateFrom IS NULL OR s.saleDate >= :dateFrom) " +
           "  AND (:dateTo IS NULL OR s.saleDate <= :dateTo) " +
           "  AND s.currency = :currency")
    BigDecimal sumSalesInPeriod(
            @Param("nonIataId") Long nonIataId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("currency") String currency
    );

    /**
     * Retrieves all sales up to an as-of date for FIFO aging calculation.
     */
    @Query("SELECT s FROM SalesTransaction s WHERE s.nonIata.id = :nonIataId " +
           "  AND s.saleDate <= :asOfDate " +
           "  AND s.currency = 'ETB' " +
           "ORDER BY s.saleDate ASC, s.id ASC")
    List<SalesTransaction> findSalesForAging(
            @Param("nonIataId") Long nonIataId,
            @Param("asOfDate") LocalDate asOfDate
    );
}
