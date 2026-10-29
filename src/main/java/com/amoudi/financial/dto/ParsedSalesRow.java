package com.amoudi.financial.dto;

import com.amoudi.financial.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Normalized row representation extracted from Galileo/BSP Sheet2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedSalesRow {
    private int sourceRowNumber;
    private String ticketNumber;
    private LocalDate saleDate;
    private String passengerName;
    private String origin;
    private String destination;
    private String route;
    private BigDecimal baseFare;
    private BigDecimal tax;
    private BigDecimal totalSale;
    private BigDecimal serviceCharge;
    private BigDecimal grandTotal;
    private BigDecimal amount; // G.TOT / compatibility alias
    @Builder.Default
    private String currency = "ETB";
    private String sourceAgencyName; // Form Of Payment Free Text
    private String pnr;
    private String pcc;
    private String son;
    private String sourceTransactionKey;
    @Builder.Default
    private TransactionType transactionType = TransactionType.SALE;
    private String originalTicketNumber;
}
