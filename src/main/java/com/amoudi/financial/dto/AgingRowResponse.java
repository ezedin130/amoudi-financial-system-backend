package com.amoudi.financial.dto;

import com.amoudi.financial.enums.NonIataStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Aging row representing aging buckets and outstanding balance for a single Non-IATA.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgingRowResponse {

    private Long nonIataId;
    private String code;
    private String name;
    private NonIataStatus status;
    private BigDecimal current;       // 0–30 days
    private BigDecimal days31To60;    // 31–60 days
    private BigDecimal days61To90;    // 61–90 days
    private BigDecimal days91To120;   // 91–120 days
    private BigDecimal days120Plus;   // 120+ days
    private BigDecimal totalSales;
    private BigDecimal totalPayments;
    private BigDecimal totalOutstanding;
    private String currency;
}
