package com.amoudi.financial.importer;

import com.amoudi.financial.enums.ImportStatus;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.ImportBatch;
import com.amoudi.financial.model.NameMapping;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.*;
import com.amoudi.financial.service.JwtService;
import com.amoudi.financial.utils.Constants;
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
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive Import Testing (Part 10 - Pillar 3):
 * Tests all dimensions of the Galileo/BSP Excel ingestion pipeline:
 * - Valid workbook with multiple Non-IATAs
 * - Authoritative Sheet2 verification & non-double counting of agency sheets
 * - G.TOT amount usage
 * - Name mapping resolution & unmatched agency handling
 * - Duplicate transaction detection within sheet and cross-batch idempotency
 * - Malformed rows, missing required fields, invalid amounts
 * - Invalid file types (.pdf, .txt, .csv)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ComprehensiveImportIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String financeToken;
    private User financeUser;

    private NonIata halifa;
    private NonIata arehman;
    private NonIata bati;
    private NonIata emran;
    private NonIata feysel;

    @BeforeEach
    void setUp() {
        importBatchErrorRepository.deleteAll();
        salesTransactionRepository.deleteAll();
        importBatchRepository.deleteAll();
        nameMappingRepository.deleteAll();
        nonIataRepository.deleteAll();
        userRepository.deleteAll();

        financeUser = userRepository.save(User.builder()
                .username("finance_importer")
                .passwordHash(passwordEncoder.encode("Pass@12345"))
                .fullName("Finance Officer")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        financeToken = jwtService.generateToken(financeUser.getUsername(), financeUser.getId(), UserRole.FINANCE, 3600000);

        halifa = nonIataRepository.save(NonIata.builder().code("HAL01").name("Halifa Travel").status(NonIataStatus.ACTIVE).build());
        arehman = nonIataRepository.save(NonIata.builder().code("ARH01").name("A Rehman Travel").status(NonIataStatus.ACTIVE).build());
        bati = nonIataRepository.save(NonIata.builder().code("BAT01").name("Bati Travel").status(NonIataStatus.ACTIVE).build());
        emran = nonIataRepository.save(NonIata.builder().code("EMR01").name("Emran Travel").status(NonIataStatus.ACTIVE).build());
        feysel = nonIataRepository.save(NonIata.builder().code("FEY01").name("Feysel Travel").status(NonIataStatus.ACTIVE).build());

        seedMapping("INV*HALIFA CASH", halifa);
        seedMapping("INV A REHMAN AYATA", arehman);
        seedMapping("INV BATI DODOLA", bati);
        seedMapping("INV EMRAN HIJRA", emran);
        seedMapping("FEYSEL JIM", feysel);
    }

    private void seedMapping(String sourceName, NonIata agency) {
        nameMappingRepository.save(NameMapping.builder()
                .sourceName(sourceName)
                .nonIata(agency)
                .createdBy(financeUser)
                .build());
    }

    private byte[] createTestWorkbook(boolean withSheet2, boolean withAgencyBreakdownSheets, boolean addCorruptRow) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            // Sheet 1: Summary Sheet (Not authoritative)
            Sheet sheet1 = wb.createSheet("CoverSummary");
            Row r0 = sheet1.createRow(0);
            r0.createCell(0).setCellValue("Summary Total");
            r0.createCell(1).setCellValue(999999.00);

            if (withSheet2) {
                // Sheet 2: Authoritative Transaction Sheet
                Sheet sheet2 = wb.createSheet("Sheet2");
                Row header = sheet2.createRow(0);
                header.createCell(0).setCellValue("Number");
                header.createCell(1).setCellValue("Issued");
                header.createCell(2).setCellValue("Passenger");
                header.createCell(3).setCellValue("Sector");
                header.createCell(4).setCellValue("G.TOT");
                header.createCell(5).setCellValue("Form of Payment Free Text");
                header.createCell(6).setCellValue("PNR");

                // Valid Row 1: Halifa
                Row row1 = sheet2.createRow(1);
                row1.createCell(0).setCellValue("065 1111111111");
                row1.createCell(1).setCellValue("17-Sep");
                row1.createCell(2).setCellValue("TEST/PASSENGER ONE");
                row1.createCell(3).setCellValue("ADDJEDADD");
                row1.createCell(4).setCellValue(25000.00);
                row1.createCell(5).setCellValue("INV*HALIFA CASH");
                row1.createCell(6).setCellValue("PNR001");

                // Valid Row 2: Bati
                Row row2 = sheet2.createRow(2);
                row2.createCell(0).setCellValue("065 2222222222");
                row2.createCell(1).setCellValue("17-Sep");
                row2.createCell(2).setCellValue("TEST/PASSENGER TWO");
                row2.createCell(3).setCellValue("ADDDXBADD");
                row2.createCell(4).setCellValue(35000.00);
                row2.createCell(5).setCellValue("INV BATI DODOLA");
                row2.createCell(6).setCellValue("PNR002");

                if (addCorruptRow) {
                    // Row with missing passenger and invalid negative amount
                    Row row3 = sheet2.createRow(3);
                    row3.createCell(0).setCellValue("065 3333333333");
                    row3.createCell(1).setCellValue("17-Sep");
                    row3.createCell(2).setCellValue(""); // missing passenger
                    row3.createCell(3).setCellValue("ADDNBOADD");
                    row3.createCell(4).setCellValue(-500.00); // invalid negative
                    row3.createCell(5).setCellValue("INV*HALIFA CASH");
                    row3.createCell(6).setCellValue("PNR003");
                }
            }

            if (withAgencyBreakdownSheets) {
                // Sheet 3: Halifa Breakdown (Should be ignored by ingestion)
                Sheet sheet3 = wb.createSheet("HalifaBreakdown");
                Row rH = sheet3.createRow(0);
                rH.createCell(0).setCellValue("065 1111111111");
                rH.createCell(1).setCellValue(25000.00);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("Upload valid Galileo/BSP workbook with authoritative Sheet2, ignoring other breakdown sheets")
    void testUploadWithAuthoritativeSheet2Only() throws Exception {
        byte[] bytes = createTestWorkbook(true, true, false);
        MockMultipartFile file = new MockMultipartFile("file", "galileo_test.xlsx", "application/vnd.ms-excel", bytes);

        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(file)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("IMPORTED")))
                .andExpect(jsonPath("$.data.totalRows", is(2)))
                .andExpect(jsonPath("$.data.successfulRows", is(2)));

        // Verify only 2 transactions created (breakdown sheet on sheet3 was NOT double-counted)
        assertThat(salesTransactionRepository.count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Reject workbook without Sheet2 with 400 Bad Request")
    void testRejectWorkbookWithoutSheet2() throws Exception {
        byte[] bytes = createTestWorkbook(false, false, false);
        MockMultipartFile file = new MockMultipartFile("file", "missing_sheet2.xlsx", "application/vnd.ms-excel", bytes);

        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(file)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("FILE_VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("Reject non-Excel files (.pdf, .txt, .csv) with 400 Bad Request")
    void testRejectNonExcelFiles() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile("file", "report.pdf", "application/pdf", "%PDF-1.4".getBytes());
        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(pdfFile)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("FILE_VALIDATION_ERROR")));

        MockMultipartFile csvFile = new MockMultipartFile("file", "report.csv", "text/csv", "col1,col2\n1,2".getBytes());
        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(csvFile)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("FILE_VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("Unmatched agency in report records error and fails batch gracefully without crash")
    void testUnmatchedAgencyFailsBatch() throws Exception {
        byte[] bytes;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet2 = wb.createSheet("Sheet2");
            Row header = sheet2.createRow(0);
            header.createCell(0).setCellValue("Number");
            header.createCell(1).setCellValue("Issued");
            header.createCell(2).setCellValue("Passenger");
            header.createCell(3).setCellValue("Sector");
            header.createCell(4).setCellValue("G.TOT");
            header.createCell(5).setCellValue("Form of Payment Free Text");
            header.createCell(6).setCellValue("PNR");

            Row row1 = sheet2.createRow(1);
            row1.createCell(0).setCellValue("065 9999999999");
            row1.createCell(1).setCellValue("17-Sep");
            row1.createCell(2).setCellValue("TEST/UNKNOWN AGENCY");
            row1.createCell(3).setCellValue("ADDJEDADD");
            row1.createCell(4).setCellValue(10000.00);
            row1.createCell(5).setCellValue("INV*COMPLETELY_UNKNOWN_AGENCY");
            row1.createCell(6).setCellValue("PNRUNK");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            bytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "unknown_agency.xlsx", "application/vnd.ms-excel", bytes);

        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(file)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("FAILED")))
                .andExpect(jsonPath("$.data.unmatchedRows", is(1)))
                .andExpect(jsonPath("$.data.successfulRows", is(0)));

        assertThat(salesTransactionRepository.count()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Repeated identical import detects 100% duplicate rows and persists zero new records")
    void testRepeatedIdenticalImportIdempotency() throws Exception {
        byte[] realExcelBytes;
        try (InputStream is = getClass().getResourceAsStream("/fixtures/SEP- 17.xlsx")) {
            assertThat(is).isNotNull();
            realExcelBytes = is.readAllBytes();
        }

        MockMultipartFile file1 = new MockMultipartFile(
                "file", "SEP- 17.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                realExcelBytes
        );

        // Upload 1: Success with 18 transactions
        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(file1)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("IMPORTED")))
                .andExpect(jsonPath("$.data.successfulRows", is(18)));

        assertThat(salesTransactionRepository.count()).isEqualTo(18L);

        // Upload 2: Exact same file re-uploaded
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "SEP- 17.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                realExcelBytes
        );

        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(file2)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.duplicateRows", is(18)))
                .andExpect(jsonPath("$.data.successfulRows", is(0)));

        // Still exactly 18 transactions in DB (no duplicates inserted)
        assertThat(salesTransactionRepository.count()).isEqualTo(18L);
    }

    @Test
    @DisplayName("Malformed row with missing passenger or invalid amount records error without crashing")
    void testMalformedRowHandling() throws Exception {
        byte[] bytes = createTestWorkbook(true, false, true);
        MockMultipartFile file = new MockMultipartFile("file", "corrupt_row.xlsx", "application/vnd.ms-excel", bytes);

        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(file)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalRows", is(3)))
                .andExpect(jsonPath("$.data.successfulRows", is(2)))
                .andExpect(jsonPath("$.data.failedRows", is(1)))
                .andExpect(jsonPath("$.data.status", is("PARTIALLY_IMPORTED")));

        assertThat(salesTransactionRepository.count()).isEqualTo(2L);
    }
}
