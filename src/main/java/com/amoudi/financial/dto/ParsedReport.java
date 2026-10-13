package com.amoudi.financial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of parsing a Galileo/BSP Excel workbook.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedReport {
    private String sheetName;
    private LocalDate reportDate;
    private int totalRowsDetected;
    @Builder.Default
    private List<ParsedSalesRow> validRows = new ArrayList<>();
    @Builder.Default
    private List<RowParsingFailure> parsingErrors = new ArrayList<>();
}
