package com.amoudi.financial.repository;

import com.amoudi.financial.enums.PaymentStatus;
import com.amoudi.financial.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
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
 * Spring Data JPA repository for Payment entity.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    /**
     * Acquires a pessimistic write lock on the payment record to prevent concurrent double-cancellations.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    List<Payment> findByNonIataId(Long nonIataId);

    List<Payment> findByNonIataIdAndStatus(Long nonIataId, PaymentStatus status);

    /**
     * Aggregates total posted payments for a specific Non-IATA account filtered by currency.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0.00) FROM Payment p " +
           "WHERE p.nonIata.id = :nonIataId " +
           "  AND p.status = com.amoudi.financial.enums.PaymentStatus.POSTED " +
           "  AND p.currency = :currency")
    BigDecimal sumPostedPaymentsByNonIataIdAndCurrency(
            @Param("nonIataId") Long nonIataId,
            @Param("currency") String currency
    );

    /**
     * Calculates the company-wide sum of all POSTED payments in a given currency.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0.00) FROM Payment p " +
           "WHERE p.status = com.amoudi.financial.enums.PaymentStatus.POSTED " +
           "  AND p.currency = :currency")
    BigDecimal sumTotalPostedPaymentsByCurrency(@Param("currency") String currency);

    /**
     * Finds distinct currencies across posted payments for a Non-IATA account.
     */
    @Query("SELECT DISTINCT p.currency FROM Payment p " +
           "WHERE p.nonIata.id = :nonIataId " +
           "  AND p.status = :status")
    List<String> findDistinctCurrenciesByNonIataIdAndStatus(
            @Param("nonIataId") Long nonIataId,
            @Param("status") PaymentStatus status
    );

    /**
     * Retrieves posted payments for a Non-IATA within an optional date range in deterministic order.
     */
    @Query("SELECT p FROM Payment p WHERE p.nonIata.id = :nonIataId " +
           "  AND p.status = com.amoudi.financial.enums.PaymentStatus.POSTED " +
           "  AND (:dateFrom IS NULL OR p.paymentDate >= :dateFrom) " +
           "  AND (:dateTo IS NULL OR p.paymentDate <= :dateTo) " +
           "ORDER BY p.paymentDate ASC, p.id ASC")
    List<Payment> findStatementPayments(
            @Param("nonIataId") Long nonIataId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );

    /**
     * Calculates the sum of posted payments for a Non-IATA before a given date (for opening balance).
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0.00) FROM Payment p " +
           "WHERE p.nonIata.id = :nonIataId " +
           "  AND p.status = com.amoudi.financial.enums.PaymentStatus.POSTED " +
           "  AND p.paymentDate < :dateFrom " +
           "  AND p.currency = :currency")
    BigDecimal sumPostedPaymentsBeforeDate(
            @Param("nonIataId") Long nonIataId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("currency") String currency
    );

    /**
     * Calculates the sum of posted payments for a Non-IATA within an optional date range.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0.00) FROM Payment p " +
           "WHERE p.nonIata.id = :nonIataId " +
           "  AND p.status = com.amoudi.financial.enums.PaymentStatus.POSTED " +
           "  AND (:dateFrom IS NULL OR p.paymentDate >= :dateFrom) " +
           "  AND (:dateTo IS NULL OR p.paymentDate <= :dateTo) " +
           "  AND p.currency = :currency")
    BigDecimal sumPostedPaymentsInPeriod(
            @Param("nonIataId") Long nonIataId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("currency") String currency
    );

    /**
     * Calculates the sum of posted payments up to an as-of date (for FIFO aging calculation).
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0.00) FROM Payment p " +
           "WHERE p.nonIata.id = :nonIataId " +
           "  AND p.status = com.amoudi.financial.enums.PaymentStatus.POSTED " +
           "  AND p.paymentDate <= :asOfDate " +
           "  AND p.currency = :currency")
    BigDecimal sumPostedPaymentsUpToDate(
            @Param("nonIataId") Long nonIataId,
            @Param("asOfDate") LocalDate asOfDate,
            @Param("currency") String currency
    );
}
