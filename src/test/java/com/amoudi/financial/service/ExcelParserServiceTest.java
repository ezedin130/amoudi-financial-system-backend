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

    @Test
    @DisplayName("Parse transactions with explicit Type column for SALE, REFUND, and EXCHANGE")
    void testParseTransactionsWithTypesSuccess() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet2");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "NO", "Number", "Issued", "Passenger", "SECTOR",
                    "Base Fare", "Tax", "Total", "S.CHR", "G.TOT",
                    "Form Of Payment Free Text", "PCC", "SON", "PNR", "Type"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // Row 1: Explicit SALE
            createTestRow(sheet, 1, "065 1111111111", "2026-10-01", "JOHN/DOE", "ADDDXB",
                    50000.0, "INV*HALIFA CASH", "PNR001", "SALE");
            // Row 2: RF (Refund) with negative G.TOT
            createTestRow(sheet, 2, "065 2222222222", "2026-10-02", "JANE/DOE", "ADDDXB",
                    -40000.0, "INV*HALIFA CASH", "PNR002", "RF");
            // Row 3: Refund (word) with negative G.TOT
            createTestRow(sheet, 3, "065 3333333333", "2026-10-03", "ALICE/SMITH", "ADDDXB",
                    -15000.0, "INV*HALIFA CASH", "PNR003", "Refund");
            // Row 4: XA (Exchange) with positive G.TOT
            createTestRow(sheet, 4, "065 4444444444", "2026-10-04", "BOB/BROWN", "ADDDXB",
                    21000.0, "INV*HALIFA CASH", "PNR004", "XA");
            // Row 5: Exchange (word) with positive G.TOT
            createTestRow(sheet, 5, "065 5555555555", "2026-10-05", "CHARLIE/GREEN", "ADDDXB",
                    10500.0, "INV*HALIFA CASH", "PNR005", "Exchange");
            // Row 6: Blank type defaults to SALE
            createTestRow(sheet, 6, "065 6666666666", "2026-10-06", "DAVID/WHITE", "ADDDXB",
                    30000.0, "INV*HALIFA CASH", "PNR006", "");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

            ParsedReport report = parserService.parse(bais, "test_types.xlsx");
            assertThat(report.getTotalRowsDetected()).isEqualTo(6);
            assertThat(report.getParsingErrors()).isEmpty();
            assertThat(report.getValidRows()).hasSize(6);

            List<ParsedSalesRow> rows = report.getValidRows();
            // Row 1
            assertThat(rows.get(0).getTransactionType()).isEqualTo(com.amoudi.financial.enums.TransactionType.SALE);
            assertThat(rows.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
            // Row 2: RF -> REFUND
            assertThat(rows.get(1).getTransactionType()).isEqualTo(com.amoudi.financial.enums.TransactionType.REFUND);
            assertThat(rows.get(1).getAmount()).isEqualByComparingTo(new BigDecimal("-40000.00"));
            assertThat(rows.get(1).getSourceTransactionKey()).contains("TYPE:REFUND");
            // Row 3: Refund -> REFUND
            assertThat(rows.get(2).getTransactionType()).isEqualTo(com.amoudi.financial.enums.TransactionType.REFUND);
            assertThat(rows.get(2).getAmount()).isEqualByComparingTo(new BigDecimal("-15000.00"));
            // Row 4: XA -> EXCHANGE
            assertThat(rows.get(3).getTransactionType()).isEqualTo(com.amoudi.financial.enums.TransactionType.EXCHANGE);
            assertThat(rows.get(3).getAmount()).isEqualByComparingTo(new BigDecimal("21000.00"));
            assertThat(rows.get(3).getSourceTransactionKey()).contains("TYPE:EXCHANGE");
            // Row 5: Exchange -> EXCHANGE
            assertThat(rows.get(4).getTransactionType()).isEqualTo(com.amoudi.financial.enums.TransactionType.EXCHANGE);
            assertThat(rows.get(4).getAmount()).isEqualByComparingTo(new BigDecimal("10500.00"));
            // Row 6: Blank -> SALE
            assertThat(rows.get(5).getTransactionType()).isEqualTo(com.amoudi.financial.enums.TransactionType.SALE);
            assertThat(rows.get(5).getAmount()).isEqualByComparingTo(new BigDecimal("30000.00"));
        }
    }

    @Test
    @DisplayName("Fail rows with invalid transaction type or invalid sign for type")
    void testParseTransactionsWithTypeErrors() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet2");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "NO", "Number", "Issued", "Passenger", "SECTOR",
                    "Base Fare", "Tax", "Total", "S.CHR", "G.TOT",
                    "Form Of Payment Free Text", "PCC", "SON", "PNR", "Type"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // Row 1: Unsupported type 'VOID'
            createTestRow(sheet, 1, "065 7777777777", "2026-10-07", "TEST/ONE", "ADDDXB",
                    10000.0, "INV*HALIFA CASH", "PNR007", "VOID");
            // Row 2: REFUND with positive amount
            createTestRow(sheet, 2, "065 8888888888", "2026-10-08", "TEST/TWO", "ADDDXB",
                    40000.0, "INV*HALIFA CASH", "PNR008", "RF");
            // Row 3: SALE with negative amount
            createTestRow(sheet, 3, "065 9999999999", "2026-10-09", "TEST/THREE", "ADDDXB",
                    -5000.0, "INV*HALIFA CASH", "PNR009", "SALE");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

            ParsedReport report = parserService.parse(bais, "test_errors.xlsx");
            assertThat(report.getTotalRowsDetected()).isEqualTo(3);
            assertThat(report.getValidRows()).isEmpty();
            assertThat(report.getParsingErrors()).hasSize(3);

            assertThat(report.getParsingErrors().get(0).getMessage())
                    .contains("Unsupported transaction type 'VOID'");
            assertThat(report.getParsingErrors().get(1).getMessage())
                    .contains("Refund G.TOT must be negative");
            assertThat(report.getParsingErrors().get(2).getMessage())
                    .contains("must be strictly positive");
        }
    }

    private void createTestRow(Sheet sheet, int rowNum, String ticket, String date, String passenger,
                               String sector, double gtot, String fop, String pnr, String type) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(rowNum);
        row.createCell(1).setCellValue(ticket);
        row.createCell(2).setCellValue(date);
        row.createCell(3).setCellValue(passenger);
        row.createCell(4).setCellValue(sector);
        row.createCell(5).setCellValue(gtot); // Base fare
        row.createCell(6).setCellValue(0.0);  // Tax
        row.createCell(7).setCellValue(gtot); // Total
        row.createCell(8).setCellValue(0.0);  // S.CHR
        row.createCell(9).setCellValue(gtot); // G.TOT
        row.createCell(10).setCellValue(fop); // FOP
        row.createCell(11).setCellValue("PCC1");
        row.createCell(12).setCellValue("SON1");
        row.createCell(13).setCellValue(pnr);
        if (type != null) {
            row.createCell(14).setCellValue(type);
        }
    }
}
