package com.amoudi.financial.dto;

import com.amoudi.financial.model.SalesTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read-only response representation of an individual ticket sales transaction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesTransactionResponse {

    private Long id;
    private Long nonIataId;
    private String nonIataCode;
    private String nonIataName;
    private String ticketNumber;
    private String transactionReference;
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
    private BigDecimal amount;
    private String currency;
    private String sourceAgencyName;
    private Integer sourceRowNumber;
    private String sourceTransactionKey;
    private Long importBatchId;
    private com.amoudi.financial.enums.TransactionType transactionType;
    private Long originalTransactionId;
    private String originalTicketNumber;
    private LocalDateTime createdAt;

    public static SalesTransactionResponse fromEntity(SalesTransaction entity) {
        BigDecimal effectiveGrandTotal = entity.getGrandTotal() != null ? entity.getGrandTotal() : entity.getAmount();
        return SalesTransactionResponse.builder()
                .id(entity.getId())
                .nonIataId(entity.getNonIata() != null ? entity.getNonIata().getId() : null)
                .nonIataCode(entity.getNonIata() != null ? entity.getNonIata().getCode() : null)
                .nonIataName(entity.getNonIata() != null ? entity.getNonIata().getName() : null)
                .ticketNumber(entity.getTicketNumber())
                .transactionReference(entity.getTransactionReference())
                .saleDate(entity.getSaleDate())
                .passengerName(entity.getPassengerName())
                .origin(entity.getOrigin())
                .destination(entity.getDestination())
                .route(entity.getRoute())
                .baseFare(entity.getBaseFare())
                .tax(entity.getTax())
                .totalSale(entity.getTotalSale())
                .serviceCharge(entity.getServiceCharge())
                .grandTotal(effectiveGrandTotal)
                .amount(effectiveGrandTotal)
                .currency(entity.getCurrency())
                .sourceAgencyName(entity.getSourceAgencyName())
                .sourceRowNumber(entity.getSourceRowNumber())
                .sourceTransactionKey(entity.getSourceTransactionKey())
                .importBatchId(entity.getImportBatch() != null ? entity.getImportBatch().getId() : null)
                .transactionType(entity.getTransactionType() != null ? entity.getTransactionType() : com.amoudi.financial.enums.TransactionType.SALE)
                .originalTransactionId(entity.getOriginalTransaction() != null ? entity.getOriginalTransaction().getId() : null)
                .originalTicketNumber(entity.getOriginalTransaction() != null ? entity.getOriginalTransaction().getTicketNumber() : null)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
