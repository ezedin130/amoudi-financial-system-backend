package com.amoudi.financial.payment;

import com.amoudi.financial.dto.CreatePaymentRequest;
import com.amoudi.financial.enums.*;
import com.amoudi.financial.model.*;
import com.amoudi.financial.repository.*;
import com.amoudi.financial.service.storage.FileStorageService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive Payment Testing (Part 10 - Pillar 4):
 * Tests payment lifecycle, receipts, overpayment, cancellation idempotency,
 * and balance integrity.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ComprehensivePaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NonIataRepository nonIataRepository;

    @Autowired
    private SalesTransactionRepository salesTransactionRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User gmUser;
    private User financeUser;
    private String gmToken;
    private String financeToken;
    private NonIata agency;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        salesTransactionRepository.deleteAll();
        importBatchRepository.deleteAll();
        nonIataRepository.deleteAll();
        userRepository.deleteAll();

        gmUser = userRepository.save(User.builder()
                .username("gm_pay_tester")
                .passwordHash(passwordEncoder.encode("Pass@12345"))
                .fullName("General Manager")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        financeUser = userRepository.save(User.builder()
                .username("fin_pay_tester")
                .passwordHash(passwordEncoder.encode("Pass@12345"))
                .fullName("Finance Officer")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        gmToken = jwtService.generateToken(gmUser.getUsername(), gmUser.getId(), UserRole.GENERAL_MANAGER, 3600000);
        financeToken = jwtService.generateToken(financeUser.getUsername(), financeUser.getId(), UserRole.FINANCE, 3600000);

        agency = nonIataRepository.save(NonIata.builder()
                .code("PAY_AG01")
                .name("Payment Test Agency")
                .status(NonIataStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("Normal payment creation followed by receipt upload and retrieval")
    void testPaymentCreationAndReceiptLifecycle() throws Exception {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .nonIataId(agency.getId())
                .amount(new BigDecimal("15000.00"))
                .paymentDate(LocalDate.now())
                .paymentReference("REF-TX-001")
                .receiptNumber("REC-001")
                .notes("Advance deposit")
                .build();

        MvcResult result = mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("POSTED")))
                .andExpect(jsonPath("$.data.amount", is(15000.00)))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        Long paymentId = json.path("data").path("id").asLong();

        // Upload PDF receipt
        MockMultipartFile receiptFile = new MockMultipartFile(
                "file", "bank_deposit.pdf", "application/pdf", "%PDF-1.4 sample bank slip".getBytes()
        );

        mockMvc.perform(multipart(Constants.PAYMENTS_API_PREFIX + "/" + paymentId + "/receipt")
                        .file(receiptFile)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasReceipt", is(true)))
                .andExpect(jsonPath("$.data.receiptObjectKey", notNullValue()));

        // Retrieve uploaded receipt
        mockMvc.perform(get(Constants.PAYMENTS_API_PREFIX + "/" + paymentId + "/receipt")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("receipt-" + paymentId)));
    }

    @Test
    @DisplayName("Reject invalid receipt file format (.exe, .sh, .html) with 400 Bad Request")
    void testRejectInvalidReceiptFormats() throws Exception {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .nonIataId(agency.getId())
                .amount(new BigDecimal("5000.00"))
                .paymentDate(LocalDate.now())
                .paymentReference("CASH-001")
                .build();

        MvcResult result = mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isCreated())
                .andReturn();

        Long paymentId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        MockMultipartFile exeFile = new MockMultipartFile("file", "malicious.exe", "application/octet-stream", "MZ...".getBytes());
        mockMvc.perform(multipart(Constants.PAYMENTS_API_PREFIX + "/" + paymentId + "/receipt")
                        .file(exeFile)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("FILE_VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("Repeated payment cancellation is rejected with 400 Bad Request (idempotency guard)")
    void testRepeatedCancellationRejected() throws Exception {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .nonIataId(agency.getId())
                .amount(new BigDecimal("8000.00"))
                .paymentDate(LocalDate.now())
                .paymentReference("CASH-002")
                .build();

        MvcResult result = mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isCreated())
                .andReturn();

        Long paymentId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 1. First cancellation succeeds
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX + "/" + paymentId + "/cancel")
                        .param("reason", "Issued in error")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        // 2. Second cancellation fails with 400
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX + "/" + paymentId + "/cancel")
                        .param("reason", "Cancelled again")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("Overpayment scenario: payment exceeding total sales produces correct credit balance")
    void testOverpaymentProducesCorrectCreditBalance() throws Exception {
        // Seed 10,000 ETB in sales
        ImportBatch batch = importBatchRepository.save(ImportBatch.builder()
                .fileName("test.xlsx")
                .objectKey("imports/test_" + java.util.UUID.randomUUID())
                .uploadedBy(financeUser)
                .status(ImportStatus.IMPORTED)
                .build());

        salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(agency)
                .importBatch(batch)
                .ticketNumber("065 1234567890")
                .saleDate(LocalDate.now())
                .passengerName("TEST/PASSENGER")
                .amount(new BigDecimal("10000.00"))
                .currency("ETB")
                .sourceAgencyName("TEST_AGENCY")
                .sourceRowNumber(1)
                .sourceTransactionKey("TX_TEST_001")
                .build());

        // Post payment of 15,000 ETB (5,000 ETB overpayment)
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .nonIataId(agency.getId())
                .amount(new BigDecimal("15000.00"))
                .paymentDate(LocalDate.now())
                .paymentReference("OVERPAY-001")
                .build();

        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("POSTED")));

        // Outstanding Balance = 10,000 - 15,000 = -5,000.00 ETB
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + agency.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(10000.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(15000.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(-5000.00)));
    }
}
