package com.amoudi.financial.controller;

import com.amoudi.financial.enums.ImportStatus;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.ImportBatch;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.SalesTransaction;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.ImportBatchRepository;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.SalesTransactionRepository;
import com.amoudi.financial.repository.UserRepository;
import com.amoudi.financial.service.JwtService;
import com.amoudi.financial.utils.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FinancialSummaryIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String financeToken;
    private String gmToken;
    private User testUser;
    private ImportBatch batch;
    private NonIata halifa;
    private NonIata agencyB;
    private NonIata noSalesAgency;

    @BeforeEach
    void setUp() {
        salesTransactionRepository.deleteAll();
        importBatchRepository.deleteAll();
        nonIataRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .username("finance_auditor")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .fullName("Finance Auditor")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        User gmUser = userRepository.save(User.builder()
                .username("gm_viewer")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .fullName("General Manager Viewer")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        financeToken = jwtService.generateToken("finance_auditor", testUser.getId(), UserRole.FINANCE, 3600000);
        gmToken = jwtService.generateToken("gm_viewer", gmUser.getId(), UserRole.GENERAL_MANAGER, 3600000);

        batch = importBatchRepository.save(ImportBatch.builder()
                .fileName("galileo_september.xlsx")
                .objectKey("s3://batches/galileo_september.xlsx")
                .uploadedBy(testUser)
                .uploadedAt(LocalDateTime.now())
                .status(ImportStatus.IMPORTED)
                .totalRows(10)
                .successfulRows(10)
                .build());

        halifa = nonIataRepository.save(NonIata.builder()
                .code("HALIFA")
                .name("Halifa Travel & Tourism")
                .status(NonIataStatus.ACTIVE)
                .build());

        agencyB = nonIataRepository.save(NonIata.builder()
                .code("AGENCY_B")
                .name("Agency B Travel")
                .status(NonIataStatus.ACTIVE)
                .build());

        noSalesAgency = nonIataRepository.save(NonIata.builder()
                .code("ZERO_SALES")
                .name("Zero Sales Agency")
                .status(NonIataStatus.INACTIVE)
                .build());
    }

    @Test
    @DisplayName("1. Non-IATA with no sales returns totalSales=0, totalPayments=0, and outstandingBalance=0")
    void financialSummary_noSales_returnsZeros() throws Exception {
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + noSalesAgency.getId() + "/financial-summary")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.nonIataCode", is("ZERO_SALES")))
                .andExpect(jsonPath("$.data.totalSales", is(0.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(0.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(0.00)))
                .andExpect(jsonPath("$.data.currency", is("ETB")));
    }

    @Test
    @DisplayName("2. One sale produces correct financial summary")
    void financialSummary_oneSale() throws Exception {
        salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(agencyB)
                .importBatch(batch)
                .ticketNumber("072-5555555555")
                .transactionReference("TXN-ONE-01")
                .saleDate(LocalDate.now())
                .amount(new BigDecimal("150000.00"))
                .currency("ETB")
                .sourceAgencyName("AGENCY B")
                .sourceRowNumber(1)
                .build());

        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + agencyB.getId() + "/financial-summary")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSales", is(150000.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(0.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(150000.00)));
    }

    @Test
    @DisplayName("3. GET /api/v1/financial-summaries returns paginated summaries with database aggregation")
    void listFinancialSummaries_paginated() throws Exception {
        salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(halifa)
                .importBatch(batch)
                .saleDate(LocalDate.now())
                .amount(new BigDecimal("350000.00"))
                .currency("ETB")
                .sourceAgencyName("HALIFA")
                .sourceRowNumber(1)
                .build());

        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.totalElements", is(3)));
    }

    @Test
    @DisplayName("4. GET /api/v1/financial-summaries with search filter")
    void listFinancialSummaries_searchFilter() throws Exception {
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .param("search", "halifa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].code", is("HALIFA")));
    }

    @Test
    @DisplayName("5. GET /api/v1/financial-summaries with status filter")
    void listFinancialSummaries_statusFilter() throws Exception {
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].code", is("ZERO_SALES")));
    }

    @Test
    @DisplayName("6. Missing Non-IATA returns 404 Not Found")
    void financialSummary_notFound() throws Exception {
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/999999/financial-summary")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("7. Unauthenticated request to financial summary returns 401 Unauthorized")
    void financialSummary_unauthenticated() throws Exception {
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + halifa.getId() + "/financial-summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("8. CRITICAL ACCEPTANCE TEST: Halifa (200k + 200k = 400k), Agency B (150k), then add Halifa 200k -> 600k (isolated)")
    void criticalAcceptanceTest_salesAndBalanceIsolation() throws Exception {
        // Step 1: Create initial sales
        // Halifa: 200,000 and 200,000
        salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(halifa)
                .importBatch(batch)
                .ticketNumber("072-H01")
                .transactionReference("TXN-H01")
                .saleDate(LocalDate.of(2026, 9, 1))
                .amount(new BigDecimal("200000.00"))
                .currency("ETB")
                .sourceAgencyName("HALIFA")
                .sourceRowNumber(1)
                .build());

        salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(halifa)
                .importBatch(batch)
                .ticketNumber("072-H02")
                .transactionReference("TXN-H02")
                .saleDate(LocalDate.of(2026, 9, 2))
                .amount(new BigDecimal("200000.00"))
                .currency("ETB")
                .sourceAgencyName("HALIFA")
                .sourceRowNumber(2)
                .build());

        // Agency B: 150,000
        salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(agencyB)
                .importBatch(batch)
                .ticketNumber("072-B01")
                .transactionReference("TXN-B01")
                .saleDate(LocalDate.of(2026, 9, 3))
                .amount(new BigDecimal("150000.00"))
                .currency("ETB")
                .sourceAgencyName("AGENCY B")
                .sourceRowNumber(3)
                .build());

        // Verify Halifa: totalSales = 400,000, totalPayments = 0, outstandingBalance = 400,000
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + halifa.getId() + "/financial-summary")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nonIataCode", is("HALIFA")))
                .andExpect(jsonPath("$.data.totalSales", is(400000.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(0.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(400000.00)));

        // Verify Agency B: totalSales = 150,000, totalPayments = 0, outstandingBalance = 150,000
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + agencyB.getId() + "/financial-summary")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nonIataCode", is("AGENCY_B")))
                .andExpect(jsonPath("$.data.totalSales", is(150000.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(0.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(150000.00)));

        // Step 2: Add another Halifa sale of 200,000
        salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(halifa)
                .importBatch(batch)
                .ticketNumber("072-H03")
                .transactionReference("TXN-H03")
                .saleDate(LocalDate.of(2026, 9, 4))
                .amount(new BigDecimal("200000.00"))
                .currency("ETB")
                .sourceAgencyName("HALIFA")
                .sourceRowNumber(4)
                .build());

        // Verify Halifa: totalSales = 600,000, outstandingBalance = 600,000
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + halifa.getId() + "/financial-summary")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nonIataCode", is("HALIFA")))
                .andExpect(jsonPath("$.data.totalSales", is(600000.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(0.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(600000.00)));

        // Verify Agency B remains untouched: totalSales = 150,000, outstandingBalance = 150,000
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + agencyB.getId() + "/financial-summary")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nonIataCode", is("AGENCY_B")))
                .andExpect(jsonPath("$.data.totalSales", is(150000.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(0.00)))
                .andExpect(jsonPath("$.data.outstandingBalance", is(150000.00)));
    }

    @Test
    @DisplayName("GET /api/v1/financial-summaries/overview returns aggregate company financial figures")
    void testGetCompanyFinancialOverview() throws Exception {
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/overview")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.currency", is("ETB")));
    }
}
