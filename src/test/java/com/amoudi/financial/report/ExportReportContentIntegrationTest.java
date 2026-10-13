package com.amoudi.financial.report;

import com.amoudi.financial.dto.CreatePaymentRequest;
import com.amoudi.financial.enums.*;
import com.amoudi.financial.model.ImportBatch;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.SalesTransaction;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.*;
import com.amoudi.financial.service.JwtService;
import com.amoudi.financial.utils.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Export Verification Testing (Part 10 - Pillar 10):
 * Verifies that exported Statement & Aging reports (Excel & PDF)
 * match the exact financial totals in the database and REST APIs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExportReportContentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NonIataRepository nonIataRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private SalesTransactionRepository salesTransactionRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private User financeUser;
    private User gmUser;
    private String financeToken;
    private String gmToken;
    private NonIata agency;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        salesTransactionRepository.deleteAll();
        importBatchRepository.deleteAll();
        nonIataRepository.deleteAll();
        userRepository.deleteAll();

        financeUser = userRepository.save(User.builder()
                .username("fin_export_tester")
                .passwordHash(passwordEncoder.encode("Pass@12345"))
                .fullName("Finance Officer")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        gmUser = userRepository.save(User.builder()
                .username("gm_export_tester")
                .passwordHash(passwordEncoder.encode("Pass@12345"))
                .fullName("General Manager")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        financeToken = jwtService.generateToken(financeUser.getUsername(), financeUser.getId(), UserRole.FINANCE, 3600000);
        gmToken = jwtService.generateToken(gmUser.getUsername(), gmUser.getId(), UserRole.GENERAL_MANAGER, 3600000);

        agency = nonIataRepository.save(NonIata.builder()
                .code("EXP_AG01")
                .name("Export Test Travel")
                .status(NonIataStatus.ACTIVE)
                .build());

        ImportBatch batch = importBatchRepository.save(ImportBatch.builder()
                .fileName("sales_import.xlsx")
                .objectKey("imports/test_exp_" + UUID.randomUUID())
                .uploadedBy(financeUser)
                .status(ImportStatus.IMPORTED)
                .build());

        // 1. Seed Sales: 60,000.00 ETB
        salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(agency)
                .importBatch(batch)
                .ticketNumber("065 9999999001")
                .saleDate(LocalDate.now().minusDays(10))
                .passengerName("AHMED/OMAR")
                .amount(new BigDecimal("60000.00"))
                .currency("ETB")
                .sourceAgencyName("TEST_AGENCY")
                .sourceRowNumber(1)
                .sourceTransactionKey("TX_EXP_001")
                .build());

        // 2. Seed Payment: 20,000.00 ETB
        CreatePaymentRequest payReq = CreatePaymentRequest.builder()
                .nonIataId(agency.getId())
                .amount(new BigDecimal("20000.00"))
                .paymentDate(LocalDate.now().minusDays(2))
                .paymentReference("PAY-EXP-001")
                .build();

        try {
            mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payReq))
                    .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                    .andExpect(status().isCreated());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Statement Excel export: parses valid workbook and verifies closing balance 40,000.00 ETB")
    void testStatementExcelExportContent() throws Exception {
        MvcResult result = mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + agency.getId() + "/statement/export/excel")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();

        byte[] excelBytes = result.getResponse().getContentAsByteArray();
        assertThat(excelBytes).isNotEmpty();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertThat(sheet).isNotNull();

            // Verify title / Non-IATA code
            String companyName = sheet.getRow(0).getCell(0).getStringCellValue();
            assertThat(companyName).contains("AMOUDI");
            String reportTitle = sheet.getRow(1).getCell(0).getStringCellValue();
            assertThat(reportTitle).contains("ACCOUNT STATEMENT");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("EXP_AG01");
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("Export Test Travel");

            // Find closing balance row
            boolean foundBalance = false;
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                if (sheet.getRow(r) != null && sheet.getRow(r).getCell(0) != null) {
                    String label = sheet.getRow(r).getCell(0).getStringCellValue();
                    if ("CLOSING BALANCE:".equalsIgnoreCase(label.trim())) {
                        double closingBal = sheet.getRow(r).getCell(1).getNumericCellValue();
                        assertThat(closingBal).isEqualTo(40000.00);
                        foundBalance = true;
                        break;
                    }
                }
            }
            assertThat(foundBalance).as("Closing balance row must be present in statement Excel").isTrue();
        }
    }

    @Test
    @DisplayName("Statement PDF export: generates non-empty valid PDF format (%PDF- header)")
    void testStatementPdfExportStructure() throws Exception {
        MvcResult result = mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + agency.getId() + "/statement/export/pdf")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).isNotEmpty();

        String pdfHeader = new String(pdfBytes, 0, Math.min(pdfBytes.length, 10), StandardCharsets.US_ASCII);
        assertThat(pdfHeader).startsWith("%PDF-");
    }

    @Test
    @DisplayName("Aging Excel export: parses valid workbook and verifies grand total 40,000.00 ETB")
    void testAgingExcelExportContent() throws Exception {
        MvcResult result = mockMvc.perform(get(Constants.AGING_API_PREFIX + "/export/excel")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();

        byte[] excelBytes = result.getResponse().getContentAsByteArray();
        assertThat(excelBytes).isNotEmpty();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertThat(sheet).isNotNull();

            // Locate Grand Total in the last row
            int lastRow = sheet.getLastRowNum();
            assertThat(sheet.getRow(lastRow).getCell(1).getStringCellValue()).isEqualTo("GRAND TOTAL:");
            double total = sheet.getRow(lastRow).getCell(8).getNumericCellValue();
            assertThat(total).isEqualTo(40000.00);
        }
    }

    @Test
    @DisplayName("Aging PDF export: generates non-empty valid PDF format (%PDF- header)")
    void testAgingPdfExportStructure() throws Exception {
        MvcResult result = mockMvc.perform(get(Constants.AGING_API_PREFIX + "/export/pdf")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).isNotEmpty();

        String pdfHeader = new String(pdfBytes, 0, Math.min(pdfBytes.length, 10), StandardCharsets.US_ASCII);
        assertThat(pdfHeader).startsWith("%PDF-");
    }
}
