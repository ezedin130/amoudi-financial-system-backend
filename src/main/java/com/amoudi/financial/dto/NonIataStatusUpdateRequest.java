package com.amoudi.financial.dto;

import com.amoudi.financial.enums.NonIataStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for changing the operational status of a Non-IATA account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NonIataStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private NonIataStatus status;
}
