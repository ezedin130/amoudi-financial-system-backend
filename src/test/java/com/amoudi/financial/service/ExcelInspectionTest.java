package com.amoudi.financial.service;

import com.amoudi.financial.dto.ParsedReport;
import com.amoudi.financial.dto.ParsedSalesRow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;

public class ExcelInspectionTest {

    @Test
    void testParserServiceDirectly() throws Exception {
        ApachePoiExcelParserService parserService = new ApachePoiExcelParserService();
        try (InputStream is = getClass().getResourceAsStream("/fixtures/SEP- 17.xlsx")) {
            Assertions.assertNotNull(is, "Sample fixture SEP- 17.xlsx must exist");
            ParsedReport report = parserService.parse(is, "SEP- 17.xlsx");

            System.out.println("Sheet Name: " + report.getSheetName());
            System.out.println("Report Date: " + report.getReportDate());
            System.out.println("Total Rows Detected: " + report.getTotalRowsDetected());
            System.out.println("Valid Rows: " + report.getValidRows().size());
            System.out.println("Parsing Errors: " + report.getParsingErrors().size());

            Assertions.assertEquals("Sheet2", report.getSheetName());
            Assertions.assertEquals(18, report.getTotalRowsDetected());
            Assertions.assertEquals(18, report.getValidRows().size());
            Assertions.assertEquals(0, report.getParsingErrors().size());

            for (ParsedSalesRow row : report.getValidRows()) {
                System.out.println(String.format("Row %2d: ticket=%s, agency='%s', amt=%s ETB, key=%s",
                        row.getSourceRowNumber(), row.getTicketNumber(), row.getSourceAgencyName(), row.getAmount(), row.getSourceTransactionKey()));
                Assertions.assertNotNull(row.getTicketNumber());
                Assertions.assertNotNull(row.getSaleDate());
                Assertions.assertNotNull(row.getAmount());
                Assertions.assertTrue(row.getAmount().compareTo(BigDecimal.ZERO) > 0);
                Assertions.assertNotNull(row.getSourceAgencyName());
                Assertions.assertNotNull(row.getSourceTransactionKey());
            }
        }
    }
}
