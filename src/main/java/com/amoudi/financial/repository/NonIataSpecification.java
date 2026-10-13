package com.amoudi.financial.repository;

import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.model.NonIata;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic specifications for searching and filtering Non-IATA records.
 */
public final class NonIataSpecification {

    private NonIataSpecification() {
        // Utility class
    }

    /**
     * Builds dynamic predicate for NonIata entities based on search term and status.
     *
     * @param search Optional search term matched against code and name (case-insensitive)
     * @param status Optional NonIataStatus filter
     * @return Composite Specification
     */
    public static Specification<NonIata> withFilters(String search, NonIataStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate codeMatch = cb.like(cb.lower(root.get("code")), pattern);
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                predicates.add(cb.or(codeMatch, nameMatch));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
