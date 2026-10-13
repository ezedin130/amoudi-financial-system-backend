package com.amoudi.financial.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload for recording a new payment from a Non-IATA sub-agent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {

    @NotNull(message = "Non-IATA ID is required")
    private Long nonIataId;

    @NotNull(message = "Payment date is required")
    @PastOrPresent(message = "Payment date cannot be in the future")
    private LocalDate paymentDate;

    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
    private BigDecimal amount;

    @Builder.Default
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    private String currency = "ETB";

    @Size(max = 100, message = "Receipt number cannot exceed 100 characters")
    private String receiptNumber;

    @Size(max = 150, message = "Payment reference cannot exceed 150 characters")
    private String paymentReference;

    @Size(max = 2000, message = "Notes cannot exceed 2000 characters")
    private String notes;
}
