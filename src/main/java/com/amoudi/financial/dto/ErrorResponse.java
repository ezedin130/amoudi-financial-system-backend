package com.amoudi.financial.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Standard error response model returned across all REST API endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @Builder.Default
    private String timestamp = Instant.now().toString();

    private int status;
    private String error;
    private String message;
    private String path;
    private String correlationId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Builder.Default
    private List<String> details = new ArrayList<>();
}
