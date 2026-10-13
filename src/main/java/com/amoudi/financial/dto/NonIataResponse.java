package com.amoudi.financial.dto;

import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.model.NonIata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Safe representation of a Non-IATA account.
 * Excludes any derived financial values (balance, sales, payments).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NonIataResponse {

    private Long id;
    private String code;
    private String name;
    private NonIataStatus status;
    private String contactName;
    private String phone;
    private String email;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NonIataResponse fromEntity(NonIata nonIata) {
        return NonIataResponse.builder()
                .id(nonIata.getId())
                .code(nonIata.getCode())
                .name(nonIata.getName())
                .status(nonIata.getStatus())
                .contactName(nonIata.getContactName())
                .phone(nonIata.getPhone())
                .email(nonIata.getEmail())
                .notes(nonIata.getNotes())
                .createdAt(nonIata.getCreatedAt())
                .updatedAt(nonIata.getUpdatedAt())
                .build();
    }
}
