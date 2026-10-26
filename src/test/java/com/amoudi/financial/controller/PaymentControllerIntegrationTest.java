package com.amoudi.financial.controller;

import com.amoudi.financial.enums.AuditAction;
import com.amoudi.financial.enums.ImportStatus;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.PaymentStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.AuditLog;
import com.amoudi.financial.model.ImportBatch;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.Payment;
import com.amoudi.financial.model.SalesTransaction;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.AuditLogRepository;
import com.amoudi.financial.repository.ImportBatchRepository;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.PaymentRepository;
import com.amoudi.financial.repository.SalesTransactionRepository;
import com.amoudi.financial.repository.UserRepository;
import com.amoudi.financial.service.JwtService;
import com.amoudi.financial.utils.Constants;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User gmUser;
    private User financeUser;
    private String gmToken;
    private String financeToken;

    private NonIata halifa;
    private NonIata bati;
    private NonIata inactiveAgency;

    @BeforeEach
    void setUp() {
        gmUser = userRepository.findByUsername("int_gm_admin").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("int_gm_admin")
                        .passwordHash(passwordEncoder.encode("Password@123"))
                        .role(UserRole.GENERAL_MANAGER)
                        .status(UserStatus.ACTIVE)
                        .fullName("GM Administrator")
                        .build())
        );

        financeUser = userRepository.findByUsername("int_finance_user").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("int_finance_user")
                        .passwordHash(passwordEncoder.encode("Password@123"))
                        .role(UserRole.FINANCE)
                        .status(UserStatus.ACTIVE)
                        .fullName("Finance Officer")
                        .build())
        );

        gmToken = jwtService.generateToken(gmUser.getUsername(), gmUser.getId(), UserRole.GENERAL_MANAGER, 3600000);
        financeToken = jwtService.generateToken(financeUser.getUsername(), financeUser.getId(), UserRole.FINANCE, 3600000);

        halifa = nonIataRepository.findByCode("INT_HAL01").orElseGet(() ->
                nonIataRepository.save(NonIata.builder()
                        .code("INT_HAL01")
                        .name("Halifa Travel Integration")
                        .status(NonIataStatus.ACTIVE)
                        .build())
        );

        bati = nonIataRepository.findByCode("INT_BAT01").orElseGet(() ->
                nonIataRepository.save(NonIata.builder()
                        .code("INT_BAT01")
                        .name("Bati Travel Integration")
                        .status(NonIataStatus.ACTIVE)
                        .build())
        );

        inactiveAgency = nonIataRepository.findByCode("INT_INACT01").orElseGet(() ->
                nonIataRepository.save(NonIata.builder()
                        .code("INT_INACT01")
                        .name("Inactive Agency Integration")
                        .status(NonIataStatus.INACTIVE)
                        .build())
        );
    }

    // =========================================================================
    // 1. AUTHENTICATION & AUTHORIZATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Security: Unauthenticated payment creation returns 401 Unauthorized")
    void unauthenticatedPaymentCreation_returns401() throws Exception {
        String json = """
                {
                    "nonIataId": %d,
                    "paymentDate": "%s",
                    "amount": 50000.00
                }
                """.formatted(halifa.getId(), LocalDate.now());

        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("Security: Unauthenticated payment listing returns 401 Unauthorized")
    void unauthenticatedPaymentListing_returns401() throws Exception {
        mockMvc.perform(get(Constants.PAYMENTS_API_PREFIX))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("Security: Finance user cannot create payment -> 403 Forbidden")
    void financeUser_createPayment_forbidden403() throws Exception {
        String json = """
                {
                    "nonIataId": %d,
                    "paymentDate": "%s",
                    "amount": 50000.00
                }
                """.formatted(halifa.getId(), LocalDate.now());

        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("ACCESS_DENIED")));
    }

    @Test
    @DisplayName("Security: Finance user cannot upload receipt -> 403 Forbidden")
    void financeUser_uploadReceipt_forbidden403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "slip.pdf", "application/pdf", "%PDF test".getBytes());

        mockMvc.perform(multipart(Constants.PAYMENTS_API_PREFIX + "/1/receipt")
                        .file(file)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("ACCESS_DENIED")));
    }

    @Test
    @DisplayName("Security: GM is authorized to cancel payment")
    void gmUser_cancelPayment_authorized() throws Exception {
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX + "/999999/cancel")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("RESOURCE_NOT_FOUND")));
    }

    // =========================================================================
    // 2. PAYMENT CREATION & VALIDATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Payment Creation: GM creates payment successfully -> 201 Created with status POSTED")
    void gmUser_createPayment_success201() throws Exception {
        String json = """
                {
                    "nonIataId": %d,
                    "paymentDate": "%s",
                    "amount": 150000.00,
                    "currency": "ETB",
                    "receiptNumber": "REC-INT-001",
                    "paymentReference": "DEP-778899",
                    "notes": "Direct bank deposit"
                }
                """.formatted(halifa.getId(), LocalDate.now());

        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.nonIataCode", is("INT_HAL01")))
                .andExpect(jsonPath("$.data.amount", is(150000.00)))
                .andExpect(jsonPath("$.data.currency", is("ETB")))
                .andExpect(jsonPath("$.data.status", is("POSTED")))
                .andExpect(jsonPath("$.data.receiptNumber", is("REC-INT-001")))
                .andExpect(jsonPath("$.data.paymentReference", is("DEP-778899")))
                .andExpect(jsonPath("$.data.createdBy", is("int_gm_admin")));

        // Verify audit log recorded
        List<AuditLog> auditLogs = auditLogRepository.findByAction(AuditAction.PAYMENT);
        assertThat(auditLogs).isNotEmpty();
    }

    @Test
    @DisplayName("Validation: Rejects payment for nonexistent Non-IATA -> 404")
    void createPayment_nonExistentNonIata_returns404() throws Exception {
        String json = """
                {
                    "nonIataId": 999999,
                    "paymentDate": "%s",
                    "amount": 10000.00
                }
                """.formatted(LocalDate.now());

        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("Validation: Rejects payment for INACTIVE Non-IATA -> 400")
    void createPayment_inactiveNonIata_returns400() throws Exception {
        String json = """
                {
                    "nonIataId": %d,
                    "paymentDate": "%s",
                    "amount": 10000.00
                }
                """.formatted(inactiveAgency.getId(), LocalDate.now());

        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("inactive Non-IATA")));
    }

    @Test
    @DisplayName("Validation: Rejects zero or negative amount -> 400")
    void createPayment_zeroOrNegativeAmount_returns400() throws Exception {
        String zeroJson = """
                {
                    "nonIataId": %d,
                    "paymentDate": "%s",
                    "amount": 0.00
                }
                """.formatted(halifa.getId(), LocalDate.now());

        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(zeroJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("Validation: Rejects future payment date -> 400")
    void createPayment_futureDate_returns400() throws Exception {
        String futureJson = """
                {
                    "nonIataId": %d,
                    "paymentDate": "%s",
                    "amount": 5000.00
                }
                """.formatted(halifa.getId(), LocalDate.now().plusDays(5));

        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(futureJson))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 3. RECEIPT ATTACHMENT & RETRIEVAL TESTS
    // =========================================================================

    @Test
    @DisplayName("Receipt: GM attaches receipt and both roles retrieve it successfully")
    void receiptWorkflow_attachAndRetrieve_success() throws Exception {
        // 1. Create payment
        Payment payment = paymentRepository.save(Payment.builder()
                .nonIata(halifa)
                .paymentDate(LocalDate.now())
                .amount(new BigDecimal("75000.00"))
                .currency("ETB")
                .status(PaymentStatus.POSTED)
                .createdBy(gmUser)
                .build());

        // 2. Attach PDF receipt
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "bank_deposit.pdf",
                "application/pdf",
                "%PDF-1.4 sample bank slip receipt".getBytes()
        );

        mockMvc.perform(multipart(Constants.PAYMENTS_API_PREFIX + "/" + payment.getId() + "/receipt")
                        .file(pdfFile)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasReceipt", is(true)))
                .andExpect(jsonPath("$.data.receiptObjectKey").isNotEmpty());

        // 3. GM retrieves receipt
        mockMvc.perform(get(Constants.PAYMENTS_API_PREFIX + "/" + payment.getId() + "/receipt")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/pdf")))
                .andExpect(content().bytes("%PDF-1.4 sample bank slip receipt".getBytes()));

        // 4. Finance retrieves receipt
        mockMvc.perform(get(Constants.PAYMENTS_API_PREFIX + "/" + payment.getId() + "/receipt")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/pdf")));
    }

    // =========================================================================
    // 4. CANCELLATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Cancellation: Finance cancels payment -> CANCELLED status, repeated cancel rejected")
    void financeUser_cancelPayment_success() throws Exception {
        Payment payment = paymentRepository.save(Payment.builder()
                .nonIata(halifa)
                .paymentDate(LocalDate.now())
                .amount(new BigDecimal("50000.00"))
                .currency("ETB")
                .status(PaymentStatus.POSTED)
                .createdBy(gmUser)
                .build());

        // Cancel payment
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX + "/" + payment.getId() + "/cancel")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        // Attempt repeated cancellation -> 400 Bad Request
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX + "/" + payment.getId() + "/cancel")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already CANCELLED")));

        // Verify audit log recorded
        List<AuditLog> cancelAudits = auditLogRepository.findByAction(AuditAction.PAYMENT_CANCEL);
        assertThat(cancelAudits).isNotEmpty();
    }

    // =========================================================================
    // 5. CRITICAL ACCEPTANCE TEST (Section 20)
    // =========================================================================

    @Test
    @DisplayName("CRITICAL ACCEPTANCE TEST: Halifa Sales 600K -> Payment 150K -> Payment 100K -> Cancel 150K")
    void criticalAcceptanceTest_fullLifecycle() throws Exception {
        // Step 1: Create 3 sales transactions of 200,000 ETB for Halifa (Total Sales = 600,000 ETB)
        ImportBatch batch = importBatchRepository.save(ImportBatch.builder()
                .fileName("HALIFA_CYCLE.xlsx")
                .objectKey("imports/test/HALIFA_CYCLE.xlsx")
                .uploadedBy(financeUser)
                .uploadedAt(LocalDateTime.now())
                .totalRows(3)
                .successfulRows(3)
                .failedRows(0)
                .duplicateRows(0)
                .unmatchedRows(0)
                .status(ImportStatus.IMPORTED)
                .build());

        salesTransactionRepository.save(SalesTransaction.builder()
                .importBatch(batch)
                .nonIata(halifa)
                .ticketNumber("071-1111111111")
                .passengerName("TEST/PASS1 MR")
                .saleDate(LocalDate.now().minusDays(5))
                .amount(new BigDecimal("200000.00"))
                .currency("ETB")
                .transactionReference("PNR001")
                .sourceAgencyName("INV*HALIFA CASH")
                .sourceRowNumber(1)
                .sourceTransactionKey("TICKET:071-1111111111|DATE:2026-09-25|AMT:200000.00|PNR:PNR001")
                .build());

        salesTransactionRepository.save(SalesTransaction.builder()
                .importBatch(batch)
                .nonIata(halifa)
                .ticketNumber("071-1111111112")
                .passengerName("TEST/PASS2 MR")
                .saleDate(LocalDate.now().minusDays(4))
                .amount(new BigDecimal("200000.00"))
                .currency("ETB")
                .transactionReference("PNR002")
                .sourceAgencyName("INV*HALIFA CASH")
                .sourceRowNumber(2)
                .sourceTransactionKey("TICKET:071-1111111112|DATE:2026-09-26|AMT:200000.00|PNR:PNR002")
                .build());

        salesTransactionRepository.save(SalesTransaction.builder()
                .importBatch(batch)
                .nonIata(halifa)
                .ticketNumber("071-1111111113")
                .passengerName("TEST/PASS3 MR")
                .saleDate(LocalDate.now().minusDays(3))
                .amount(new BigDecimal("200000.00"))
                .currency("ETB")
                .transactionReference("PNR003")
                .sourceAgencyName("INV*HALIFA CASH")
                .sourceRowNumber(3)
                .sourceTransactionKey("TICKET:071-1111111113|DATE:2026-09-27|AMT:200000.00|PNR:PNR003")
                .build());

        // Initial check: Total Sales = 600,000, Total Payments = 0, Outstanding = 600,000
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + halifa.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(600000.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(0.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(600000.00)));

        // Step 2: GM records Payment 1: 150,000 ETB
        String payment1Json = """
                {
                    "nonIataId": %d,
                    "paymentDate": "%s",
                    "amount": 150000.00,
                    "currency": "ETB",
                    "receiptNumber": "REC-150K",
                    "paymentReference": "REF-150K"
                }
                """.formatted(halifa.getId(), LocalDate.now().minusDays(2));

        String payment1ResponseStr = mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payment1Json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("POSTED")))
                .andReturn().getResponse().getContentAsString();

        Long payment1Id = objectMapper.readTree(payment1ResponseStr).get("data").get("id").asLong();

        // Verification after Payment 1: Sales = 600,000, Payments = 150,000, Outstanding = 450,000
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + halifa.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(600000.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(150000.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(450000.00)));

        // Step 3: GM records Payment 2: 100,000 ETB
        String payment2Json = """
                {
                    "nonIataId": %d,
                    "paymentDate": "%s",
                    "amount": 100000.00,
                    "currency": "ETB",
                    "receiptNumber": "REC-100K",
                    "paymentReference": "REF-100K"
                }
                """.formatted(halifa.getId(), LocalDate.now().minusDays(1));

        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payment2Json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("POSTED")));

        // Verification after Payment 2: Sales = 600,000, Payments = 250,000, Outstanding = 350,000
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + halifa.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(600000.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(250000.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(350000.00)));

        // Step 4: Finance cancels Payment 1 (150,000 ETB)
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX + "/" + payment1Id + "/cancel")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        // Verification after Payment 1 cancelled: Sales = 600,000, Payments = 100,000, Outstanding = 500,000
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + halifa.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(600000.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(100000.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(500000.00)));

        // Verification of Bulk Financial Summaries (GET /api/v1/financial-summaries)
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX)
                        .param("search", "INT_HAL01")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].code", is("INT_HAL01")))
                .andExpect(jsonPath("$.data.content[0].totalSales", is(600000.00)))
                .andExpect(jsonPath("$.data.content[0].totalPayments", is(100000.00)))
                .andExpect(jsonPath("$.data.content[0].outstandingBalance", is(500000.00)));

        // Verify another Non-IATA (Bati) has independent balance of 0.00
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + bati.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(0.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(0.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(0.00)));

        // Verify cancelled payment remains in payment history
        mockMvc.perform(get(Constants.PAYMENTS_API_PREFIX)
                        .param("nonIataId", halifa.getId().toString())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)));
    }
}
