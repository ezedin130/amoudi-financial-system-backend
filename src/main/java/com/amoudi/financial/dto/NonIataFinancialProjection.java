package com.amoudi.financial.dto;

import com.amoudi.financial.enums.NonIataStatus;

import java.math.BigDecimal;

/**
 * Spring Data JPA projection for database-level financial summary aggregations.
 */
public interface NonIataFinancialProjection {

    Long getNonIataId();

    String getCode();

    String getName();

    NonIataStatus getStatus();

    BigDecimal getTotalSales();

    BigDecimal getTotalPayments();
}
