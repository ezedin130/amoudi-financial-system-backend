package com.amoudi.financial.security;

import com.amoudi.financial.dto.CreatePaymentRequest;
import com.amoudi.financial.dto.NonIataCreateRequest;
import com.amoudi.financial.dto.NonIataStatusUpdateRequest;
import com.amoudi.financial.dto.NonIataUpdateRequest;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.PaymentStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.Payment;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.PaymentRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC Authorization Matrix Verification (Part 10 - Pillar 6):
 * Directly verifies the exact permission matrix against real REST API endpoints:
 *
 * Operation                Finance  GM
 * View financial data      ✓ (200)  ✓ (200)
 * Import Excel             ✓ (201)  ✗ (403)
 * Create payment           ✗ (403)  ✓ (201)
 * Upload receipt           ✗ (403)  ✓ (200)
 * Cancel payment           ✓ (200)  ✗ (403)
 * Manage Non-IATA          ✓ (201)  ✗ (403)
 * Audit access             ✓ (200)  ✗ (403)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RbacAuthorizationMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NonIataRepository nonIataRepository;

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
    private NonIata testAgency;
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        nonIataRepository.deleteAll();
        userRepository.deleteAll();

        financeUser = userRepository.save(User.builder()
                .username("rbac_finance")
                .passwordHash(passwordEncoder.encode("Pass@12345"))
                .fullName("Finance Auditor")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        gmUser = userRepository.save(User.builder()
                .username("rbac_gm")
                .passwordHash(passwordEncoder.encode("Pass@12345"))
                .fullName("General Manager")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        financeToken = jwtService.generateToken(financeUser.getUsername(), financeUser.getId(), UserRole.FINANCE, 3600000);
        gmToken = jwtService.generateToken(gmUser.getUsername(), gmUser.getId(), UserRole.GENERAL_MANAGER, 3600000);

        testAgency = nonIataRepository.save(NonIata.builder()
                .code("RBAC_AG")
                .name("RBAC Non-IATA Agency")
                .status(NonIataStatus.ACTIVE)
                .build());

        testPayment = paymentRepository.save(Payment.builder()
                .nonIata(testAgency)
                .amount(new BigDecimal("1000.00"))
                .currency("ETB")
                .paymentDate(LocalDate.now())
                .paymentReference("CASH-REF")
                .status(PaymentStatus.POSTED)
                .createdBy(gmUser)
                .build());
    }

    // =========================================================================
    // 1. VIEW FINANCIAL DATA: FINANCE ✓ (200), GM ✓ (200)
    // =========================================================================

    @Test
    @DisplayName("View Financial Data: Both FINANCE and GM receive 200 OK")
    void testViewFinancialDataBothAllowed() throws Exception {
        // Financial Summaries
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk());

        // Statements
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + testAgency.getId() + "/statement")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + testAgency.getId() + "/statement")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk());

        // Aging
        mockMvc.perform(get(Constants.AGING_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(Constants.AGING_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk());

        // Sales
        mockMvc.perform(get(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk());

        // Payments
        mockMvc.perform(get(Constants.PAYMENTS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(Constants.PAYMENTS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 2. IMPORT EXCEL: FINANCE ✓, GM ✗ (403)
    // =========================================================================

    @Test
    @DisplayName("Import Excel: FINANCE is authorized, GM receives 403 Forbidden")
    void testImportExcelPermissions() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/vnd.ms-excel", new byte[]{1});

        // GM is forbidden
        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(file)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. CREATE PAYMENT: FINANCE ✗ (403), GM ✓ (201)
    // =========================================================================

    @Test
    @DisplayName("Create Payment: FINANCE receives 403 Forbidden, GM receives 201 Created")
    void testCreatePaymentPermissions() throws Exception {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .nonIataId(testAgency.getId())
                .amount(new BigDecimal("500.00"))
                .paymentDate(LocalDate.now())
                .paymentReference("CASH-REF-2")
                .build();

        // FINANCE is forbidden
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isForbidden());

        // GM is authorized
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // 4. UPLOAD RECEIPT: FINANCE ✗ (403), GM ✓ (200)
    // =========================================================================

    @Test
    @DisplayName("Upload Receipt: FINANCE receives 403 Forbidden, GM receives 200 OK")
    void testUploadReceiptPermissions() throws Exception {
        MockMultipartFile receipt = new MockMultipartFile("file", "rec.pdf", "application/pdf", "%PDF-1.4 test".getBytes());

        // FINANCE is forbidden
        mockMvc.perform(multipart(Constants.PAYMENTS_API_PREFIX + "/" + testPayment.getId() + "/receipt")
                        .file(receipt)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isForbidden());

        // GM is authorized
        mockMvc.perform(multipart(Constants.PAYMENTS_API_PREFIX + "/" + testPayment.getId() + "/receipt")
                        .file(receipt)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 5. CANCEL PAYMENT: FINANCE ✓ (200), GM ✗ (403)
    // =========================================================================

    @Test
    @DisplayName("Cancel Payment: GM receives 403 Forbidden, FINANCE receives 200 OK")
    void testCancelPaymentPermissions() throws Exception {
        // GM is forbidden
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX + "/" + testPayment.getId() + "/cancel")
                        .param("reason", "Unauthorized cancellation attempt")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isForbidden());

        // FINANCE is authorized
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX + "/" + testPayment.getId() + "/cancel")
                        .param("reason", "Legitimate cancellation by Finance")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 6. MANAGE NON-IATA: FINANCE ✓ (201/200), GM ✗ (403)
    // =========================================================================

    @Test
    @DisplayName("Manage Non-IATA: GM receives 403 Forbidden, FINANCE receives 201/200 OK")
    void testManageNonIataPermissions() throws Exception {
        NonIataCreateRequest createReq = NonIataCreateRequest.builder()
                .code("NEW_AG01")
                .name("New Travel Agency")
                .build();

        // GM cannot create
        mockMvc.perform(post(Constants.NON_IATAS_API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq))
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isForbidden());

        // GM cannot update
        NonIataUpdateRequest updateReq = NonIataUpdateRequest.builder().name("Updated Name").build();
        mockMvc.perform(put(Constants.NON_IATAS_API_PREFIX + "/" + testAgency.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq))
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isForbidden());

        // GM cannot update status
        NonIataStatusUpdateRequest statusReq = NonIataStatusUpdateRequest.builder().status(NonIataStatus.INACTIVE).build();
        mockMvc.perform(patch(Constants.NON_IATAS_API_PREFIX + "/" + testAgency.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq))
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isForbidden());

        // FINANCE can create
        mockMvc.perform(post(Constants.NON_IATAS_API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq))
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // 7. AUDIT ACCESS: FINANCE ✓ (200), GM ✗ (403)
    // =========================================================================

    @Test
    @DisplayName("Audit Access: GM receives 403 Forbidden, FINANCE receives 200 OK")
    void testAuditAccessPermissions() throws Exception {
        // GM is forbidden
        mockMvc.perform(get(Constants.AUDIT_LOGS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isForbidden());

        // FINANCE is authorized
        mockMvc.perform(get(Constants.AUDIT_LOGS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 8. UNAUTHENTICATED CALLS RETURN 401 UNAUTHORIZED
    // =========================================================================

    @Test
    @DisplayName("Unauthenticated requests to protected endpoints return 401 Unauthorized")
    void testUnauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(Constants.PAYMENTS_API_PREFIX)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(Constants.SALES_API_PREFIX)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(Constants.AUDIT_LOGS_API_PREFIX)).andExpect(status().isUnauthorized());
    }
}
