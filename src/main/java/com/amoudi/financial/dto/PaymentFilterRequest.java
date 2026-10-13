package com.amoudi.financial.dto;

import com.amoudi.financial.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Filter criteria for querying payment records.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFilterRequest {

    private Long nonIataId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate paymentDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;

    private String receiptNumber;
    private String paymentReference;
    private PaymentStatus status;
}
