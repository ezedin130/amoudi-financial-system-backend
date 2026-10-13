package com.amoudi.financial.repository;

import com.amoudi.financial.enums.PaymentStatus;
import com.amoudi.financial.model.Payment;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic specifications for searching and filtering Payment records.
 */
public final class PaymentSpecification {

    private PaymentSpecification() {
        // Prevent instantiation
    }

    /**
     * Builds dynamic predicate based on optional filter parameters.
     */
    public static Specification<Payment> withFilters(
            Long nonIataId,
            LocalDate paymentDate,
            LocalDate dateFrom,
            LocalDate dateTo,
            String receiptNumber,
            String paymentReference,
            PaymentStatus status
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (nonIataId != null) {
                predicates.add(cb.equal(root.get("nonIata").get("id"), nonIataId));
            }

            if (paymentDate != null) {
                predicates.add(cb.equal(root.get("paymentDate"), paymentDate));
            } else {
                if (dateFrom != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("paymentDate"), dateFrom));
                }
                if (dateTo != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("paymentDate"), dateTo));
                }
            }

            if (receiptNumber != null && !receiptNumber.trim().isEmpty()) {
                String pattern = "%" + receiptNumber.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("receiptNumber")), pattern));
            }

            if (paymentReference != null && !paymentReference.trim().isEmpty()) {
                String pattern = "%" + paymentReference.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("paymentReference")), pattern));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
