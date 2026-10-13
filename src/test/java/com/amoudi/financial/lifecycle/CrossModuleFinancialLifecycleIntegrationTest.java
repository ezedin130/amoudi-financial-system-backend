package com.amoudi.financial.lifecycle;

import com.amoudi.financial.dto.CreatePaymentRequest;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.NameMapping;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.*;
import com.amoudi.financial.service.JwtService;
import com.amoudi.financial.utils.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test validating the cross-module financial lifecycle:
 * Excel Import -> Sales Transactions -> Financial Summary -> Payment ->
 * Statement -> Aging -> Payment Cancellation -> Statement -> Aging -> Exports.
 *
 * Proves that all representations reconcile at every step according to the invariant:
 * Outstanding Balance = Total Sales - Total Posted Payments
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CrossModuleFinancialLifecycleIntegrationTest {

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
        paymentRepository.deleteAll();
        importBatchErrorRepository.deleteAll();
        salesTransactionRepository.deleteAll();
        importBatchRepository.deleteAll();
        nameMappingRepository.deleteAll();
        nonIataRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create authenticated users
        financeUser = userRepository.save(User.builder()
                .username("finance_auditor")
                .passwordHash(passwordEncoder.encode("Pass@12345"))
                .fullName("Finance Officer")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        gmUser = userRepository.save(User.builder()
                .username("gm_exec")
                .passwordHash(passwordEncoder.encode("Pass@12345"))
                .fullName("General Manager")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        financeToken = jwtService.generateToken(financeUser.getUsername(), financeUser.getId(), UserRole.FINANCE, 3600000);
        gmToken = jwtService.generateToken(gmUser.getUsername(), gmUser.getId(), UserRole.GENERAL_MANAGER, 3600000);

        // 2. Seed Non-IATAs matching SEP- 17.xlsx agencies
        halifa = nonIataRepository.save(NonIata.builder().code("HAL01").name("Halifa Travel").status(NonIataStatus.ACTIVE).build());
        arehman = nonIataRepository.save(NonIata.builder().code("ARH01").name("A Rehman Travel").status(NonIataStatus.ACTIVE).build());
        bati = nonIataRepository.save(NonIata.builder().code("BAT01").name("Bati Travel").status(NonIataStatus.ACTIVE).build());
        emran = nonIataRepository.save(NonIata.builder().code("EMR01").name("Emran Travel").status(NonIataStatus.ACTIVE).build());
        feysel = nonIataRepository.save(NonIata.builder().code("FEY01").name("Feysel Travel").status(NonIataStatus.ACTIVE).build());

        // 3. Seed name mappings
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

    @Test
    @DisplayName("Complete Cross-Module Financial Lifecycle: Import -> Sales -> Summary -> Payment -> Statement -> Aging -> Cancel -> Reconcile")
    void testCompleteFinancialLifecycleAndReconciliation() throws Exception {
        // =====================================================================
        // STEP 1: Upload SEP- 17.xlsx (18 rows, ETB 1,282,870.00 total)
        // =====================================================================
        byte[] excelBytes;
        try (InputStream is = getClass().getResourceAsStream("/fixtures/SEP- 17.xlsx")) {
            assertThat(is).as("SEP- 17.xlsx fixture must be available").isNotNull();
            excelBytes = is.readAllBytes();
        }

        MockMultipartFile excelFile = new MockMultipartFile(
                "file", "SEP- 17.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(excelFile)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("IMPORTED")))
                .andExpect(jsonPath("$.data.successfulRows", is(18)));

        assertThat(salesTransactionRepository.count()).isEqualTo(18L);

        // =====================================================================
        // STEP 2: Verify Initial Financial Summary (Sales only, Payments = 0)
        // Halifa: 119,842.00, Bati: 143,673.00, Total Company: 1,282,870.00
        // =====================================================================
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + halifa.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(119842.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(0.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(119842.00)));

        // Verify Halifa Statement
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + halifa.getId() + "/statement")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(119842.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(0.00)))
                .andExpect(jsonPath("$.data.closingBalance", is(119842.00)));

        // Verify Halifa Aging
        mockMvc.perform(get(Constants.AGING_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOutstanding", is(1282870.00)));

        // =====================================================================
        // STEP 3: Post Payment #1 for Halifa: 50,000.00 ETB by GM
        // =====================================================================
        CreatePaymentRequest paymentReq1 = CreatePaymentRequest.builder()
                .nonIataId(halifa.getId())
                .amount(new BigDecimal("50000.00"))
                .paymentDate(LocalDate.now())
                .paymentReference("HAL-PAY-001")
                .notes("Partial payment for ticket sales")
                .build();

        MvcResult payResult1 = mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentReq1))
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("POSTED")))
                .andExpect(jsonPath("$.data.amount", is(50000.00)))
                .andReturn();

        JsonNode payNode1 = objectMapper.readTree(payResult1.getResponse().getContentAsString());
        Long paymentId1 = payNode1.path("data").path("id").asLong();

        // =====================================================================
        // STEP 4: Reconcile Halifa After Payment #1
        // Expected Halifa Outstanding = 119,842.00 - 50,000.00 = 69,842.00
        // Expected Company Balance = 1,282,870.00 - 50,000.00 = 1,232,870.00
        // =====================================================================
        // A) Financial Summary
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + halifa.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(119842.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(50000.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(69842.00)));

        // B) Statement
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + halifa.getId() + "/statement")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(119842.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(50000.00)))
                .andExpect(jsonPath("$.data.closingBalance", is(69842.00)));

        // C) Company Aging
        mockMvc.perform(get(Constants.AGING_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOutstanding", is(1232870.00)));

        // =====================================================================
        // STEP 5: Post Payment #2 for Bati: 43,673.00 ETB by GM
        // Bati sales: 143,673.00 -> balance becomes 100,000.00
        // Company total payments: 93,673.00 -> company balance becomes 1,189,197.00
        // =====================================================================
        CreatePaymentRequest paymentReq2 = CreatePaymentRequest.builder()
                .nonIataId(bati.getId())
                .amount(new BigDecimal("43673.00"))
                .paymentDate(LocalDate.now())
                .paymentReference("BAT-CHK-002")
                .notes("Bati partial payment")
                .build();

        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentReq2))
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("POSTED")));

        // Bati Summary
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + bati.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(143673.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(43673.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(100000.00)));

        // Company Aging Grand Total
        mockMvc.perform(get(Constants.AGING_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOutstanding", is(1189197.00)));

        // =====================================================================
        // STEP 6: Cancel Payment #1 (Halifa 50,000.00 ETB) by Finance
        // Halifa balance must restore to 119,842.00
        // Company balance must restore to 1,239,197.00
        // =====================================================================
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX + "/" + paymentId1 + "/cancel")
                        .param("reason", "Bank transaction reversed by issuer")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        // Halifa Summary Reconciled
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + halifa.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(119842.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(0.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(119842.00)));

        // Halifa Statement Reconciled (Cancelled payment excluded from balance)
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + halifa.getId() + "/statement")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(119842.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(0.00)))
                .andExpect(jsonPath("$.data.closingBalance", is(119842.00)));

        // Company Aging Grand Total Reconciled
        mockMvc.perform(get(Constants.AGING_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOutstanding", is(1239197.00)));

        // =====================================================================
        // STEP 7: Export Statement & Aging (Excel and PDF)
        // =====================================================================
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + halifa.getId() + "/statement/export/excel")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + halifa.getId() + "/statement/export/pdf")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));

        mockMvc.perform(get(Constants.AGING_API_PREFIX + "/export/excel")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        mockMvc.perform(get(Constants.AGING_API_PREFIX + "/export/pdf")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }
}
