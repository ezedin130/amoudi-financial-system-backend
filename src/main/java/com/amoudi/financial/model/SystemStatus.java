package com.amoudi.financial.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Foundation entity mapping the system_status table.
 * Used exclusively for foundation and migration connectivity verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "system_status")
public class SystemStatus {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "status_name", nullable = false, length = 100, unique = true)
    private String statusName;

    @Column(name = "status_value", nullable = false, length = 255)
    private String statusValue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
