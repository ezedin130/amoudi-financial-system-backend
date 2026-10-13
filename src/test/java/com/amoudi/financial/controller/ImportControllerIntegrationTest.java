package com.amoudi.financial.controller;

import com.amoudi.financial.dto.NameMappingRequest;
import com.amoudi.financial.enums.ImportStatus;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.*;
import com.amoudi.financial.repository.*;
import com.amoudi.financial.service.JwtService;
import com.amoudi.financial.utils.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImportControllerIntegrationTest {

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

    @Autowired
    private ObjectMapper objectMapper;

    private String financeToken;
    private String gmToken;
    private User financeUser;
    private User gmUser;

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

        // 1. Create users
        financeUser = userRepository.save(User.builder()
                .username("finance_usr")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .fullName("Finance Officer")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        gmUser = userRepository.save(User.builder()
                .username("gm_usr")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .fullName("General Manager")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        financeToken = jwtService.generateToken("finance_usr", financeUser.getId(), UserRole.FINANCE, 3600000);
        gmToken = jwtService.generateToken("gm_usr", gmUser.getId(), UserRole.GENERAL_MANAGER, 3600000);

        // 2. Seed Non-IATAs corresponding to sample file agencies
        halifa = nonIataRepository.save(NonIata.builder().code("HAL01").name("Halifa Travel").status(NonIataStatus.ACTIVE).build());
        arehman = nonIataRepository.save(NonIata.builder().code("ARH01").name("A Rehman Travel").status(NonIataStatus.ACTIVE).build());
        bati = nonIataRepository.save(NonIata.builder().code("BAT01").name("Bati Travel").status(NonIataStatus.ACTIVE).build());
        emran = nonIataRepository.save(NonIata.builder().code("EMR01").name("Emran Travel").status(NonIataStatus.ACTIVE).build());
        feysel = nonIataRepository.save(NonIata.builder().code("FEY01").name("Feysel Travel").status(NonIataStatus.ACTIVE).build());

        // 3. Seed Name Mappings
        createMapping("INV*HALIFA CASH", halifa);
        createMapping("INV A REHMAN AYATA", arehman);
        createMapping("INV BATI DODOLA", bati);
        createMapping("INV EMRAN HIJRA", emran);
        createMapping("FEYSEL JIM", feysel);
    }

    private void createMapping(String sourceName, NonIata nonIata) {
        nameMappingRepository.save(NameMapping.builder()
                .sourceName(sourceName)
                .nonIata(nonIata)
                .createdBy(financeUser)
                .build());
    }

    // =========================================================================
    // AUTHORIZATION TESTS
    // =========================================================================

    @Test
    @DisplayName("GENERAL_MANAGER receives 403 Forbidden when attempting report upload")
    void testUploadForbiddenForGeneralManager() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/vnd.ms-excel", new byte[]{1, 2, 3});

        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(file)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated upload receives 401 Unauthorized")
    void testUploadUnauthorizedWhenNoToken() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/vnd.ms-excel", new byte[]{1, 2, 3});

        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX).file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Both FINANCE and GENERAL_MANAGER can view import history")
    void testBothRolesCanViewImportHistory() throws Exception {
        mockMvc.perform(get(Constants.IMPORTS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(Constants.IMPORTS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // FILE VALIDATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Reject empty file upload with 400 Bad Request")
    void testRejectEmptyFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.xlsx", "application/vnd.ms-excel", new byte[0]);

        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(emptyFile)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("FILE_VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("Reject non-Excel file extension with 400 Bad Request")
    void testRejectNonExcelExtension() throws Exception {
        MockMultipartFile txtFile = new MockMultipartFile("file", "test.txt", "text/plain", "hello world".getBytes());

        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(txtFile)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("FILE_VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("Reject workbook without Sheet2 with 400 Bad Request")
    void testRejectWorkbookWithoutSheet2() throws Exception {
        byte[] bytes;
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("OnlySheet1");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            bytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "no_sheet2.xlsx", "application/vnd.ms-excel", bytes);

        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(file)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("FILE_VALIDATION_ERROR")))
                .andExpect(jsonPath("$.message", containsString("Sheet2")));
    }

    // =========================================================================
    // REAL SEP- 17.xlsx ACCEPTANCE TEST
    // =========================================================================

    @Test
    @DisplayName("Acceptance Test: Upload real SEP- 17.xlsx, verify Sheet2 authority, G.TOT amounts, and multi-agency segregation")
    void testUploadRealSep17Report() throws Exception {
        byte[] realExcelBytes;
        try (InputStream is = getClass().getResourceAsStream("/fixtures/SEP- 17.xlsx")) {
            assertThat(is).as("SEP- 17.xlsx must be in test resources").isNotNull();
            realExcelBytes = is.readAllBytes();
        }

        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "SEP- 17.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                realExcelBytes
        );

        // 1. Upload as FINANCE
        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(multipartFile)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.fileName", is("SEP- 17.xlsx")))
                .andExpect(jsonPath("$.data.totalRows", is(18)))
                .andExpect(jsonPath("$.data.successfulRows", is(18)))
                .andExpect(jsonPath("$.data.duplicateRows", is(0)))
                .andExpect(jsonPath("$.data.unmatchedRows", is(0)))
                .andExpect(jsonPath("$.data.failedRows", is(0)))
                .andExpect(jsonPath("$.data.status", is("IMPORTED")));

        // 2. Verify exactly 18 SalesTransaction records persisted in database
        assertThat(salesTransactionRepository.count()).isEqualTo(18L);

        // 3. Verify sales transactions visible through GET /api/v1/sales
        mockMvc.perform(get(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", is(18)));

        // 4. Verify financial summaries per Non-IATA
        // Halifa: 1 row = 119,842.00 ETB
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + halifa.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(119842.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(119842.00)));

        // A Rehman: 1 row = 47,891.00 ETB
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + arehman.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(47891.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(47891.00)));

        // Bati: 3 rows of 47,891.00 = 143,673.00 ETB
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + bati.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(143673.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(143673.00)));

        // Emran: 11 rows of 74,728.00 = 822,008.00 ETB
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + emran.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(822008.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(822008.00)));

        // Feysel: 2 rows of 74,728.00 = 149,456.00 ETB
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + feysel.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(149456.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(149456.00)));

        // Total sales across all Non-IATAs = 1,282,870.00 ETB
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", is(5)));
    }

    // =========================================================================
    // RE-UPLOAD & DUPLICATE PREVENTION ACCEPTANCE TEST
    // =========================================================================

    @Test
    @DisplayName("Mandatory Acceptance: Re-uploading exact same report does NOT double debt and reports 18 duplicates")
    void testReUploadDuplicatePrevention() throws Exception {
        byte[] realExcelBytes;
        try (InputStream is = getClass().getResourceAsStream("/fixtures/SEP- 17.xlsx")) {
            realExcelBytes = is.readAllBytes();
        }

        MockMultipartFile file1 = new MockMultipartFile(
                "file", "SEP- 17.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", realExcelBytes);

        // First upload -> Success
        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(file1)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.successfulRows", is(18)))
                .andExpect(jsonPath("$.data.duplicateRows", is(0)));

        BigDecimal initialHalifaSales = salesTransactionRepository.sumAmountByNonIataIdAndCurrency(halifa.getId(), "ETB");
        assertThat(initialHalifaSales).isEqualByComparingTo(new BigDecimal("119842.00"));

        // Second upload of exact same file -> Duplicate detection
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "SEP- 17.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", realExcelBytes);

        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(file2)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalRows", is(18)))
                .andExpect(jsonPath("$.data.successfulRows", is(0)))
                .andExpect(jsonPath("$.data.duplicateRows", is(18)))
                .andExpect(jsonPath("$.data.status", is("FAILED")));

        // Transaction count remains 18 (NOT 36!)
        assertThat(salesTransactionRepository.count()).isEqualTo(18L);

        // Halifa balance is STILL 119,842.00 (NOT doubled to 239,684.00!)
        BigDecimal halifaSalesAfterReupload = salesTransactionRepository.sumAmountByNonIataIdAndCurrency(halifa.getId(), "ETB");
        assertThat(halifaSalesAfterReupload).isEqualByComparingTo(new BigDecimal("119842.00"));

        // Emran balance is STILL 822,008.00 (NOT doubled!)
        BigDecimal emranSalesAfterReupload = salesTransactionRepository.sumAmountByNonIataIdAndCurrency(emran.getId(), "ETB");
        assertThat(emranSalesAfterReupload).isEqualByComparingTo(new BigDecimal("822008.00"));
    }

    // =========================================================================
    // IMMUTABILITY TEST
    // =========================================================================

    @Test
    @DisplayName("Verify SalesTransaction records are immutable: no DELETE endpoint exists")
    void testNoDeleteEndpointForSales() throws Exception {
        mockMvc.perform(delete(Constants.SALES_API_PREFIX + "/1")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isMethodNotAllowed());
    }

    // =========================================================================
    // NAME MAPPINGS CONTROLLER TESTS
    // =========================================================================

    @Test
    @DisplayName("Finance can register new name mapping; GM receives 403 Forbidden")
    void testNameMappingManagement() throws Exception {
        NameMappingRequest req = NameMappingRequest.builder()
                .sourceName("INV NEW TEST AGENCY")
                .nonIataId(halifa.getId())
                .build();

        // GM forbidden
        mockMvc.perform(post(Constants.NAME_MAPPINGS_API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isForbidden());

        // Finance allowed
        mockMvc.perform(post(Constants.NAME_MAPPINGS_API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sourceName", is("INV NEW TEST AGENCY")))
                .andExpect(jsonPath("$.data.nonIataCode", is("HAL01")));

        // List mappings accessible by both
        mockMvc.perform(get(Constants.NAME_MAPPINGS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(6)));
    }
}
