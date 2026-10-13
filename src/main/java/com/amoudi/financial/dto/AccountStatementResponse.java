package com.amoudi.financial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Account statement representing chronological financial transactions for a Non-IATA.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatementResponse {

    private Long nonIataId;
    private String nonIataCode;
    private String nonIataName;
    private String currency;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private BigDecimal openingBalance;
    private BigDecimal totalSales;
    private BigDecimal totalPayments;
    private BigDecimal closingBalance;
    private int transactionCount;
    private List<StatementTransactionResponse> transactions;
}
