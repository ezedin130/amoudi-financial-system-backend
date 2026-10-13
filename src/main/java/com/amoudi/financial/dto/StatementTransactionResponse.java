package com.amoudi.financial.dto;

import com.amoudi.financial.enums.StatementTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Line item in an account statement with running balance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatementTransactionResponse {

    private LocalDate transactionDate;
    private StatementTransactionType type;
    private String reference;
    private String description;
    private BigDecimal debit;
    private BigDecimal credit;
    private BigDecimal runningBalance;
    private BigDecimal baseFare;
    private BigDecimal tax;
    private BigDecimal totalSale;
    private BigDecimal serviceCharge;
    private BigDecimal grandTotal;
    private Long sourceTransactionId;
    private String currency;
}
