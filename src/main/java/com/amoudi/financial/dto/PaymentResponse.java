package com.amoudi.financial.dto;

import com.amoudi.financial.enums.PaymentStatus;
import com.amoudi.financial.model.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response payload representing a recorded payment and its receipt status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private Long nonIataId;
    private String nonIataCode;
    private String nonIataName;
    private LocalDate paymentDate;
    private BigDecimal amount;
    private String currency;
    private String receiptNumber;
    private String paymentReference;
    private String notes;
    private String receiptObjectKey;
    private boolean hasReceipt;
    private PaymentStatus status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentResponse of(Payment payment) {
        if (payment == null) {
            return null;
        }

        Long nonIataId = null;
        String nonIataCode = null;
        String nonIataName = null;
        if (payment.getNonIata() != null) {
            nonIataId = payment.getNonIata().getId();
            nonIataCode = payment.getNonIata().getCode();
            nonIataName = payment.getNonIata().getName();
        }

        String createdByUsername = null;
        if (payment.getCreatedBy() != null) {
            createdByUsername = payment.getCreatedBy().getUsername();
        }

        return PaymentResponse.builder()
                .id(payment.getId())
                .nonIataId(nonIataId)
                .nonIataCode(nonIataCode)
                .nonIataName(nonIataName)
                .paymentDate(payment.getPaymentDate())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .receiptNumber(payment.getReceiptNumber())
                .paymentReference(payment.getPaymentReference())
                .notes(payment.getNotes())
                .receiptObjectKey(payment.getReceiptObjectKey())
                .hasReceipt(payment.getReceiptObjectKey() != null && !payment.getReceiptObjectKey().trim().isEmpty())
                .status(payment.getStatus())
                .createdBy(createdByUsername)
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
