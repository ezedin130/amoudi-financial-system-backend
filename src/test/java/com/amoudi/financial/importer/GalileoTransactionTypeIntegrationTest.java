package com.amoudi.financial.importer;

import com.amoudi.financial.dto.AccountStatementResponse;
import com.amoudi.financial.dto.AgingReportResponse;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.TransactionType;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.NameMapping;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.SalesTransaction;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.*;
import com.amoudi.financial.service.AgingService;
import com.amoudi.financial.service.JwtService;
import com.amoudi.financial.service.StatementService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class GalileoTransactionTypeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NonIataRepository nonIataRepository;

    @Autowired
    private NameMappingRepository nameMappingRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private ImportBatchErrorRepository importBatchErrorRepository;

    @Autowired
    private SalesTransactionRepository salesTransactionRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private StatementService statementService;

    @Autowired
    private AgingService agingService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User financeUser;
    private String financeToken;
    private NonIata agencyA;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        salesTransactionRepository.deleteAll();
        importBatchErrorRepository.deleteAll();
        importBatchRepository.deleteAll();
        nameMappingRepository.deleteAll();
        nonIataRepository.deleteAll();
        userRepository.deleteAll();

        financeUser = userRepository.save(User.builder()
                .username("galileo_tester")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Galileo Tester")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        financeToken = jwtService.generateToken(financeUser.getUsername(), financeUser.getId(), UserRole.FINANCE, 3600000);

        agencyA = nonIataRepository.save(NonIata.builder()
                .code("AGY001")
                .name("AL-AMOUDI TRAVEL TEST")
                .status(NonIataStatus.ACTIVE)
                .build());

        nameMappingRepository.save(NameMapping.builder()
                .sourceName("INV*TEST AGENCY")
                .nonIata(agencyA)
                .createdBy(financeUser)
                .build());
    }

    @Test
    @DisplayName("Scenario A: 5 sales at 50,000 + 1 refund at -40,000 -> Outstanding = 210,000 (penalty not deducted twice)")
    void testScenarioA_RefundReducesOutstandingBalance() throws Exception {
        byte[] excelBytes;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet2");
            createHeader(sheet);

            // 5 sales at 50,000
            for (int i = 1; i <= 5; i++) {
                addTransactionRow(sheet, i, "065 100000000" + i, "2026-10-01", "PAX/TEST " + i,
                        50000.0, "INV*TEST AGENCY", "PNR00" + i, "SALE");
            }
            // 1 refund at -40,000 linked to ticket 065 1000000001
            addTransactionRow(sheet, 6, "065 1000000001", "2026-10-02", "PAX/TEST 1",
                    -40000.0, "INV*TEST AGENCY", "PNR001", "RF");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            excelBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "scenario_a.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        mockMvc.perform(multipart("/api/v1/imports")
                        .file(file)
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("IMPORTED"))
                .andExpect(jsonPath("$.data.totalRows").value(6))
                .andExpect(jsonPath("$.data.successfulRows").value(6))
                .andExpect(jsonPath("$.data.salesCount").value(5))
                .andExpect(jsonPath("$.data.refundsCount").value(1))
                .andExpect(jsonPath("$.data.exchangesCount").value(0));

        // Verify statement calculation
        AccountStatementResponse statement = statementService.getStatement(
                agencyA.getId(), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));

        assertThat(statement.getTotalSales()).isEqualByComparingTo(new BigDecimal("250000.00"));
        assertThat(statement.getTotalRefunds()).isEqualByComparingTo(new BigDecimal("40000.00"));
        assertThat(statement.getNetSales()).isEqualByComparingTo(new BigDecimal("210000.00"));
        assertThat(statement.getTotalPayments()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(statement.getClosingBalance()).isEqualByComparingTo(new BigDecimal("210000.00"));

        // Verify aging calculation
        AgingReportResponse aging = agingService.getAgingReport(LocalDate.parse("2026-10-31"), null, null, null);
        assertThat(aging.getTotalOutstanding()).isEqualByComparingTo(new BigDecimal("210000.00"));

        // Verify original ticket linkage
        List<SalesTransaction> refunds = salesTransactionRepository.findAll().stream()
                .filter(t -> t.getTransactionType() == TransactionType.REFUND)
                .toList();
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getOriginalTransaction()).isNotNull();
        assertThat(refunds.get(0).getOriginalTransaction().getTicketNumber()).isEqualTo("065 1000000001");
    }

    @Test
    @DisplayName("Scenario B: 5 sales at 50,000 + 1 exchange at 21,000 -> Outstanding = 271,000 (ticket not double-counted)")
    void testScenarioB_ExchangeAddsAdditionalCollectionOnly() throws Exception {
        byte[] excelBytes;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet2");
            createHeader(sheet);

            // 5 sales at 50,000 = 250,000
            for (int i = 1; i <= 5; i++) {
                addTransactionRow(sheet, i, "065 200000000" + i, "2026-10-01", "PAX/TEST " + i,
                        50000.0, "INV*TEST AGENCY", "PNR00" + i, "SALE");
            }
            // 1 exchange with additional collection 21,000 (20,000 fare difference + 1,000 service charge)
            addTransactionRow(sheet, 6, "065 2000000001", "2026-10-02", "PAX/TEST 1",
                    21000.0, "INV*TEST AGENCY", "PNR001", "XA");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            excelBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "scenario_b.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        mockMvc.perform(multipart("/api/v1/imports")
                        .file(file)
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("IMPORTED"))
                .andExpect(jsonPath("$.data.salesCount").value(5))
                .andExpect(jsonPath("$.data.refundsCount").value(0))
                .andExpect(jsonPath("$.data.exchangesCount").value(1));

        // Statement calculation: gross sales 271,000, net sales 271,000, closing balance 271,000
        AccountStatementResponse statement = statementService.getStatement(
                agencyA.getId(), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));

        assertThat(statement.getTotalSales()).isEqualByComparingTo(new BigDecimal("271000.00"));
        assertThat(statement.getTotalRefunds()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(statement.getNetSales()).isEqualByComparingTo(new BigDecimal("271000.00"));
        assertThat(statement.getClosingBalance()).isEqualByComparingTo(new BigDecimal("271000.00"));

        // Aging report total
        AgingReportResponse aging = agingService.getAgingReport(LocalDate.parse("2026-10-31"), null, null, null);
        assertThat(aging.getTotalOutstanding()).isEqualByComparingTo(new BigDecimal("271000.00"));

        // Verify original ticket linkage
        List<SalesTransaction> exchanges = salesTransactionRepository.findAll().stream()
                .filter(t -> t.getTransactionType() == TransactionType.EXCHANGE)
                .toList();
        assertThat(exchanges).hasSize(1);
        assertThat(exchanges.get(0).getOriginalTransaction()).isNotNull();
        assertThat(exchanges.get(0).getOriginalTransaction().getTicketNumber()).isEqualTo("065 2000000001");
    }

    @Test
    @DisplayName("Scenario C: Credit balance - agency with 10,000 sale and -40,000 refund results in -30,000 balance")
    void testScenarioC_CreditBalancePermitted() throws Exception {
        byte[] excelBytes;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet2");
            createHeader(sheet);

            addTransactionRow(sheet, 1, "065 3000000001", "2026-10-01", "PAX/TEST 1",
                    10000.0, "INV*TEST AGENCY", "PNR001", "SALE");
            addTransactionRow(sheet, 2, "065 3000000002", "2026-10-02", "PAX/TEST 2",
                    -40000.0, "INV*TEST AGENCY", "PNR002", "Refund");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            excelBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "scenario_c.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        mockMvc.perform(multipart("/api/v1/imports")
                        .file(file)
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("IMPORTED"));

        AccountStatementResponse statement = statementService.getStatement(
                agencyA.getId(), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));

        assertThat(statement.getTotalSales()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(statement.getTotalRefunds()).isEqualByComparingTo(new BigDecimal("40000.00"));
        assertThat(statement.getNetSales()).isEqualByComparingTo(new BigDecimal("-30000.00"));
        assertThat(statement.getClosingBalance()).isEqualByComparingTo(new BigDecimal("-30000.00"));

        AgingReportResponse aging = agingService.getAgingReport(LocalDate.parse("2026-10-31"), null, null, null);
        assertThat(aging.getTotalOutstanding()).isEqualByComparingTo(new BigDecimal("-30000.00"));
    }

    @Test
    @DisplayName("Scenario D & Re-import: Re-importing exact same file is idempotent and flags duplicates")
    void testReimportIdempotency() throws Exception {
        byte[] excelBytes;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet2");
            createHeader(sheet);

            addTransactionRow(sheet, 1, "065 4000000001", "2026-10-01", "PAX/TEST 1",
                    50000.0, "INV*TEST AGENCY", "PNR001", "SALE");
            addTransactionRow(sheet, 2, "065 4000000002", "2026-10-02", "PAX/TEST 2",
                    -20000.0, "INV*TEST AGENCY", "PNR002", "RF");
            addTransactionRow(sheet, 3, "065 4000000003", "2026-10-03", "PAX/TEST 3",
                    15000.0, "INV*TEST AGENCY", "PNR003", "XA");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            excelBytes = baos.toByteArray();
        }

        MockMultipartFile file1 = new MockMultipartFile("file", "batch_first.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        mockMvc.perform(multipart("/api/v1/imports")
                        .file(file1)
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("IMPORTED"))
                .andExpect(jsonPath("$.data.successfulRows").value(3))
                .andExpect(jsonPath("$.data.salesCount").value(1))
                .andExpect(jsonPath("$.data.refundsCount").value(1))
                .andExpect(jsonPath("$.data.exchangesCount").value(1));

        // Second import of exact same records
        MockMultipartFile file2 = new MockMultipartFile("file", "batch_second.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        mockMvc.perform(multipart("/api/v1/imports")
                        .file(file2)
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.successfulRows").value(0))
                .andExpect(jsonPath("$.data.duplicateRows").value(3));

        // Verify database still has exactly 3 records
        assertThat(salesTransactionRepository.count()).isEqualTo(3);
    }

    private void createHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] headers = {
                "NO", "Number", "Issued", "Passenger", "SECTOR",
                "Base Fare", "Tax", "Total", "S.CHR", "G.TOT",
                "Form Of Payment Free Text", "PCC", "SON", "PNR", "Type"
        };
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
    }

    private void addTransactionRow(Sheet sheet, int rowNum, String ticket, String date, String passenger,
                                   double gtot, String fop, String pnr, String type) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(rowNum);
        row.createCell(1).setCellValue(ticket);
        row.createCell(2).setCellValue(date);
        row.createCell(3).setCellValue(passenger);
        row.createCell(4).setCellValue("ADDDXB");
        row.createCell(5).setCellValue(gtot);
        row.createCell(6).setCellValue(0.0);
        row.createCell(7).setCellValue(gtot);
        row.createCell(8).setCellValue(0.0);
        row.createCell(9).setCellValue(gtot);
        row.createCell(10).setCellValue(fop);
        row.createCell(11).setCellValue("PCC1");
        row.createCell(12).setCellValue("SON1");
        row.createCell(13).setCellValue(pnr);
        row.createCell(14).setCellValue(type);
    }
}
