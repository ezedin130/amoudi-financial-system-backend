package com.amoudi.financial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating a NameMapping rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NameMappingRequest {

    @NotBlank(message = "Source name is required")
    @Size(max = 255, message = "Source name cannot exceed 255 characters")
    private String sourceName;

    @NotNull(message = "Non-IATA ID is required")
    private Long nonIataId;
}
