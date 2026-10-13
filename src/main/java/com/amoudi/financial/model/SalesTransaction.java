package com.amoudi.financial.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents an individual ticket sale transaction parsed from an uploaded sales report.
 * Immutable financial record (no updated_at).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sales_transactions")
public class SalesTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "non_iata_id", nullable = false)
    private NonIata nonIata;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id", nullable = false)
    private ImportBatch importBatch;

    @Column(name = "ticket_number", length = 100)
    private String ticketNumber;

    @Column(name = "transaction_reference", length = 150)
    private String transactionReference;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "passenger_name", length = 255)
    private String passengerName;

    @Column(name = "origin", length = 10)
    private String origin;

    @Column(name = "destination", length = 10)
    private String destination;

    @Column(name = "route", length = 50)
    private String route;

    @Column(name = "base_fare", precision = 18, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "tax", precision = 18, scale = 2)
    private BigDecimal tax;

    @Column(name = "total_sale", precision = 18, scale = 2)
    private BigDecimal totalSale;

    @Column(name = "service_charge", precision = 18, scale = 2)
    private BigDecimal serviceCharge;

    @Column(name = "grand_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "ETB";

    @Column(name = "source_agency_name", nullable = false, length = 255)
    private String sourceAgencyName;

    @Column(name = "source_row_number", nullable = false)
    private int sourceRowNumber;

    @Column(name = "source_transaction_key", length = 255)
    private String sourceTransactionKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.currency == null) {
            this.currency = "ETB";
        }
        if (this.grandTotal == null && this.amount != null) {
            this.grandTotal = this.amount;
        } else if (this.amount == null && this.grandTotal != null) {
            this.amount = this.grandTotal;
        }
    }
}
