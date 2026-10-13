package com.amoudi.financial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response payload representing a NameMapping rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NameMappingResponse {
    private Long id;
    private String sourceName;
    private Long nonIataId;
    private String nonIataCode;
    private String nonIataName;
    private Long createdById;
    private String createdByUsername;
    private LocalDateTime createdAt;
}
