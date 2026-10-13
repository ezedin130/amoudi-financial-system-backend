package com.amoudi.financial.model;

import com.amoudi.financial.enums.NonIataStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Represents a Non-IATA sub-agent account.
 * Note: Outstanding balance is strictly NOT stored as a field;
 * it is computed on-demand from ledger transactions to preserve financial integrity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "non_iatas")
public class NonIata extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private NonIataStatus status = NonIataStatus.ACTIVE;

    @Column(name = "contact_name", length = 200)
    private String contactName;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
