package com.amoudi.financial.repository;

import com.amoudi.financial.model.SalesTransaction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic specifications for searching and filtering SalesTransaction records.
 */
public final class SalesTransactionSpecification {

    private SalesTransactionSpecification() {
        // Prevent instantiation
    }

    /**
     * Builds dynamic predicate based on optional filter parameters.
     */
    public static Specification<SalesTransaction> withFilters(
            Long nonIataId,
            LocalDate saleDate,
            LocalDate startDate,
            LocalDate endDate,
            String ticketNumber,
            String transactionReference,
            Long importBatchId,
            com.amoudi.financial.enums.TransactionType transactionType
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (nonIataId != null) {
                predicates.add(cb.equal(root.get("nonIata").get("id"), nonIataId));
            }

            if (saleDate != null) {
                predicates.add(cb.equal(root.get("saleDate"), saleDate));
            } else {
                if (startDate != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("saleDate"), startDate));
                }
                if (endDate != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("saleDate"), endDate));
                }
            }

            if (ticketNumber != null && !ticketNumber.trim().isEmpty()) {
                String pattern = "%" + ticketNumber.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("ticketNumber")), pattern));
            }

            if (transactionReference != null && !transactionReference.trim().isEmpty()) {
                String pattern = "%" + transactionReference.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("transactionReference")), pattern));
            }

            if (importBatchId != null) {
                predicates.add(cb.equal(root.get("importBatch").get("id"), importBatchId));
            }

            if (transactionType != null) {
                predicates.add(cb.equal(root.get("transactionType"), transactionType));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
