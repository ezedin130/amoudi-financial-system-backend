package com.amoudi.financial.service;

import com.amoudi.financial.dto.ParsedReport;
import com.amoudi.financial.dto.ParsedSalesRow;
import com.amoudi.financial.exception.FileValidationException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelParserServiceTest {

    private ApachePoiExcelParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new ApachePoiExcelParserService();
    }

    @Test
    @DisplayName("Parse real SEP- 17.xlsx fixture successfully with 18 transactions from Sheet2")
    void testParseRealSampleFile() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/SEP- 17.xlsx")) {
            assertThat(is).as("SEP- 17.xlsx fixture must be present").isNotNull();
            ParsedReport report = parserService.parse(is, "SEP- 17.xlsx");

            assertThat(report.getSheetName()).isEqualTo("Sheet2");
            assertThat(report.getTotalRowsDetected()).isEqualTo(18);
            assertThat(report.getValidRows()).hasSize(18);
            assertThat(report.getParsingErrors()).isEmpty();

            // Verify first transaction: HALIFA with evaluated formula G.TOT = 119842.00
            ParsedSalesRow row1 = report.getValidRows().get(0);
            assertThat(row1.getSourceRowNumber()).isEqualTo(2);
            assertThat(row1.getTicketNumber()).isEqualTo("065 5282705392");
            assertThat(row1.getSaleDate()).isEqualTo("2026-09-17");
            assertThat(row1.getPassengerName()).isEqualTo("ABDULHAKIM/FUAD MOHAMMED");
            assertThat(row1.getRoute()).isEqualTo("ADDJEDDXBJEDADD");
            assertThat(row1.getOrigin()).isEqualTo("ADD");
            assertThat(row1.getDestination()).isEqualTo("ADD");
            assertThat(row1.getAmount()).isEqualByComparingTo(new BigDecimal("119842.00"));
            assertThat(row1.getSourceAgencyName()).isEqualTo("INV*HALIFA CASH");
            assertThat(row1.getPnr()).isEqualTo("J3PPWN");
            assertThat(row1.getSourceTransactionKey()).isEqualTo("TICKET:0655282705392|DATE:2026-09-17|AMT:119842.00|PNR:J3PPWN");

            // Verify supporting sheets were NOT imported: valid rows count is exactly 18
            List<String> agencies = report.getValidRows().stream()
                    .map(ParsedSalesRow::getSourceAgencyName)
                    .distinct()
                    .toList();
            assertThat(agencies).containsExactlyInAnyOrder(
                    "INV*HALIFA CASH",
                    "INV A REHMAN AYATA",
                    "INV BATI DODOLA",
                    "INV EMRAN HIJRA",
                    "FEYSEL JIM"
            );
        }
    }

    @Test
    @DisplayName("Fail when workbook does not contain Sheet2")
    void testFailWhenSheet2Missing() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("Transactions");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            assertThatThrownBy(() -> parserService.parse(bais, "test.xlsx"))
                    .isInstanceOf(FileValidationException.class)
                    .hasMessageContaining("does not contain authoritative sheet 'Sheet2'");
        }
    }

    @Test
    @DisplayName("Fail when required column header is missing in Sheet2")
    void testFailWhenRequiredColumnMissing() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet2");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Number");
            header.createCell(1).setCellValue("Issued");
            header.createCell(2).setCellValue("Passenger");
            // Missing SECTOR, G.TOT, etc.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            assertThatThrownBy(() -> parserService.parse(bais, "test.xlsx"))
                    .isInstanceOf(FileValidationException.class)
                    .hasMessageContaining("missing required header columns");
        }
    }

    @Test
    @DisplayName("Fail when file input stream is corrupted or non-Excel")
    void testFailWhenCorruptedInput() {
        ByteArrayInputStream bais = new ByteArrayInputStream("Not an excel file binary".getBytes());
        assertThatThrownBy(() -> parserService.parse(bais, "corrupted.xlsx"))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("Invalid or corrupted Excel file");
    }

    @Test
    @DisplayName("Fail when input stream is null")
    void testFailWhenNullInputStream() {
        assertThatThrownBy(() -> parserService.parse(null, "report.xlsx"))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("cannot be null");
    }
}
