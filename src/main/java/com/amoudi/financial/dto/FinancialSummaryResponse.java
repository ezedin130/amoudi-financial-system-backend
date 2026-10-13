package com.amoudi.financial.dto;

import com.amoudi.financial.enums.NonIataStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Financial summary presentation DTO for a Non-IATA account.
 * Encapsulates dynamically calculated totals: totalSales, totalPayments, and outstandingBalance.
 *
 * Implements dual-naming convention ('code' & 'nonIataCode', 'name' & 'nonIataName')
 * to ensure consistency across single and bulk dashboard/report consumers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FinancialSummaryResponse {

    private Long nonIataId;
    private String nonIataCode;
    private String nonIataName;
    private String code;
    private String name;
    private NonIataStatus status;
    private BigDecimal totalSales;
    private BigDecimal totalPayments;
    private BigDecimal outstandingBalance;
    private String currency;

    public static FinancialSummaryResponse of(
            Long nonIataId,
            String code,
            String name,
            NonIataStatus status,
            BigDecimal totalSales,
            BigDecimal totalPayments,
            String currency
    ) {
        BigDecimal safeSales = (totalSales != null ? totalSales : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal safePayments = (totalPayments != null ? totalPayments : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal balance = safeSales.subtract(safePayments).setScale(2, RoundingMode.HALF_UP);

        return FinancialSummaryResponse.builder()
                .nonIataId(nonIataId)
                .nonIataCode(code)
                .nonIataName(name)
                .code(code)
                .name(name)
                .status(status)
                .totalSales(safeSales)
                .totalPayments(safePayments)
                .outstandingBalance(balance)
                .currency(currency != null ? currency : "ETB")
                .build();
    }
}
