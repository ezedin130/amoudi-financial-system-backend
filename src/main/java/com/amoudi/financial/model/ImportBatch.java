package com.amoudi.financial.model;

import com.amoudi.financial.enums.ImportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents an uploaded Galileo/BSP sales report batch and its ingestion metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "import_batches")
public class ImportBatch extends BaseEntity {

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(name = "report_date")
    private LocalDate reportDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Builder.Default
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "successful_rows", nullable = false)
    private int successfulRows;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;

    @Column(name = "unmatched_rows", nullable = false)
    private int unmatchedRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @Builder.Default
    @Column(name = "sales_count", nullable = false)
    private int salesCount = 0;

    @Builder.Default
    @Column(name = "refunds_count", nullable = false)
    private int refundsCount = 0;

    @Builder.Default
    @Column(name = "exchanges_count", nullable = false)
    private int exchangesCount = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ImportStatus status = ImportStatus.UPLOADED;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;
}
