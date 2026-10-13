package com.amoudi.financial.service;

import com.amoudi.financial.dto.ParsedReport;

import java.io.InputStream;

/**
 * Service contract for parsing Galileo/BSP sales reports.
 * Isolated from financial business rules and database persistence.
 */
public interface ExcelParserService {

    /**
     * Parses the authoritative Sheet2 of an uploaded Galileo/BSP Excel workbook.
     * Supporting sheets (e.g. EMRAN, BATI, HALIFA) are strictly ignored for transaction creation.
     *
     * @param inputStream binary stream of the Excel workbook
     * @param originalFilename original name of the uploaded file
     * @return structured report containing parsed valid rows and row parsing errors
     */
    ParsedReport parse(InputStream inputStream, String originalFilename);
}
