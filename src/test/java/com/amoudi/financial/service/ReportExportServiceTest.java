package com.amoudi.financial.service;

import com.amoudi.financial.dto.AccountStatementResponse;
import com.amoudi.financial.dto.AgingReportResponse;
import com.amoudi.financial.dto.AgingRowResponse;
import com.amoudi.financial.dto.StatementTransactionResponse;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.StatementTransactionType;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportExportServiceTest {

    private ReportExportService reportExportService;

    @BeforeEach
    void setUp() {
        reportExportService = new ReportExportServiceImpl();
    }

    @Test
    @DisplayName("Excel export for account statement creates valid workbook with expected sheets and rows")
    void testExportStatementToExcel() throws IOException {
        StatementTransactionResponse tx1 = StatementTransactionResponse.builder()
                .transactionDate(LocalDate.of(2026, 9, 10))
                .type(StatementTransactionType.SALE)
                .reference("T-1001")
                .description("Ticket Sale: Passenger A")
                .debit(new BigDecimal("15000.00"))
                .credit(BigDecimal.ZERO)
                .runningBalance(new BigDecimal("15000.00"))
                .sourceTransactionId(1L)
                .currency("ETB")
                .build();

        StatementTransactionResponse tx2 = StatementTransactionResponse.builder()
                .transactionDate(LocalDate.of(2026, 9, 15))
                .type(StatementTransactionType.PAYMENT)
                .reference("REC-001")
                .description("Payment (Cash)")
                .debit(BigDecimal.ZERO)
                .credit(new BigDecimal("5000.00"))
                .runningBalance(new BigDecimal("10000.00"))
                .sourceTransactionId(2L)
                .currency("ETB")
                .build();

        AccountStatementResponse statement = AccountStatementResponse.builder()
                .nonIataId(1L)
                .nonIataCode("HAL01")
                .nonIataName("Halifa Agency")
                .currency("ETB")
                .dateFrom(LocalDate.of(2026, 9, 1))
                .dateTo(LocalDate.of(2026, 9, 30))
                .openingBalance(BigDecimal.ZERO)
                .totalSales(new BigDecimal("15000.00"))
                .totalPayments(new BigDecimal("5000.00"))
                .closingBalance(new BigDecimal("10000.00"))
                .transactionCount(2)
                .transactions(List.of(tx1, tx2))
                .build();

        byte[] bytes = reportExportService.exportStatementToExcel(statement);

        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(100);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheet("Account Statement");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getLastRowNum()).isGreaterThan(5);
        }
    }

    @Test
    @DisplayName("Excel export for aging report creates valid workbook with summary rows")
    void testExportAgingToExcel() throws IOException {
        AgingRowResponse row = AgingRowResponse.builder()
                .nonIataId(1L)
                .code("HAL01")
                .name("Halifa Agency")
                .status(NonIataStatus.ACTIVE)
                .current(new BigDecimal("30000.00"))
                .days31To60(new BigDecimal("20000.00"))
                .days61To90(BigDecimal.ZERO)
                .days91To120(BigDecimal.ZERO)
                .days120Plus(BigDecimal.ZERO)
                .totalOutstanding(new BigDecimal("50000.00"))
                .currency("ETB")
                .build();

        AgingReportResponse report = AgingReportResponse.builder()
                .asOfDate(LocalDate.of(2026, 9, 30))
                .totalCurrent(new BigDecimal("30000.00"))
                .totalDays31To60(new BigDecimal("20000.00"))
                .totalDays61To90(BigDecimal.ZERO)
                .totalDays91To120(BigDecimal.ZERO)
                .totalDays120Plus(BigDecimal.ZERO)
                .totalOutstanding(new BigDecimal("50000.00"))
                .currency("ETB")
                .totalAccounts(1)
                .rows(List.of(row))
                .build();

        byte[] bytes = reportExportService.exportAgingToExcel(report);

        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(100);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheet("Aging Report");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getLastRowNum()).isGreaterThan(3);
        }
    }

    @Test
    @DisplayName("PDF export for statement creates valid PDF document")
    void testExportStatementToPdf() {
        AccountStatementResponse statement = AccountStatementResponse.builder()
                .nonIataId(1L)
                .nonIataCode("HAL01")
                .nonIataName("Halifa Agency")
                .currency("ETB")
                .dateFrom(LocalDate.of(2026, 9, 1))
                .dateTo(LocalDate.of(2026, 9, 30))
                .openingBalance(BigDecimal.ZERO)
                .totalSales(new BigDecimal("1000.00"))
                .totalPayments(new BigDecimal("500.00"))
                .closingBalance(new BigDecimal("500.00"))
                .transactionCount(0)
                .transactions(List.of())
                .build();

        byte[] bytes = reportExportService.exportStatementToPdf(statement);

        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(100);

        String header = new String(bytes, 0, 5, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("PDF export for aging report creates valid landscape PDF document")
    void testExportAgingToPdf() {
        AgingReportResponse report = AgingReportResponse.builder()
                .asOfDate(LocalDate.of(2026, 9, 30))
                .totalCurrent(new BigDecimal("1000.00"))
                .totalDays31To60(BigDecimal.ZERO)
                .totalDays61To90(BigDecimal.ZERO)
                .totalDays91To120(BigDecimal.ZERO)
                .totalDays120Plus(BigDecimal.ZERO)
                .totalOutstanding(new BigDecimal("1000.00"))
                .currency("ETB")
                .totalAccounts(0)
                .rows(List.of())
                .build();

        byte[] bytes = reportExportService.exportAgingToPdf(report);

        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(100);

        String header = new String(bytes, 0, 5, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF-");
    }
}
