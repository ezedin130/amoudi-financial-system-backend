package com.amoudi.financial.controller;

import com.amoudi.financial.enums.ImportStatus;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.PaymentStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.ImportBatch;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.Payment;
import com.amoudi.financial.model.SalesTransaction;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.ImportBatchRepository;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.PaymentRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StatementControllerIntegrationTest {

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

    private String financeToken;
    private String gmToken;
    private NonIata halifa;
    private User testUser;
    private ImportBatch batch;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        salesTransactionRepository.deleteAll();
        importBatchRepository.deleteAll();
        nonIataRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .username("finance_user")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .fullName("Finance User")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        User gmUser = userRepository.save(User.builder()
                .username("gm_user")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .fullName("General Manager")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        financeToken = Constants.TOKEN_BEARER_PREFIX + jwtService.generateToken(testUser.getUsername(), testUser.getId(), testUser.getRole(), 3600000L);
        gmToken = Constants.TOKEN_BEARER_PREFIX + jwtService.generateToken(gmUser.getUsername(), gmUser.getId(), gmUser.getRole(), 3600000L);

        batch = importBatchRepository.save(ImportBatch.builder()
                .fileName("test_batch.xlsx")
                .objectKey("s3://batches/test_batch.xlsx")
                .uploadedBy(testUser)
                .uploadedAt(LocalDateTime.now())
                .status(ImportStatus.IMPORTED)
                .totalRows(10)
                .successfulRows(10)
                .build());

        halifa = nonIataRepository.save(NonIata.builder()
                .code("HAL01")
                .name("Halifa Travel Agency")
                .status(NonIataStatus.ACTIVE)
                .build());

        // August 15 sale (Prior period): 50,000 ETB
        salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(halifa)
                .importBatch(batch)
                .ticketNumber("T-100")
                .passengerName("Old Passenger")
                .saleDate(LocalDate.of(2026, 8, 15))
                .amount(new BigDecimal("50000.00"))
                .currency("ETB")
                .sourceAgencyName("HALIFA TRAVEL")
                .sourceRowNumber(1)
                .build());

        // August 20 payment (Prior period): 10,000 ETB
        paymentRepository.save(Payment.builder()
                .nonIata(halifa)
                .paymentDate(LocalDate.of(2026, 8, 20))
                .amount(new BigDecimal("10000.00"))
                .currency("ETB")
                .status(PaymentStatus.POSTED)
                .createdBy(testUser)
                .build());

        // September 5 sale: 30,000 ETB
        salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(halifa)
                .importBatch(batch)
                .ticketNumber("T-200")
                .passengerName("Sep Passenger")
                .saleDate(LocalDate.of(2026, 9, 5))
                .amount(new BigDecimal("30000.00"))
                .currency("ETB")
                .sourceAgencyName("HALIFA TRAVEL")
                .sourceRowNumber(2)
                .build());

        // September 10 payment: 15,000 ETB
        paymentRepository.save(Payment.builder()
                .nonIata(halifa)
                .paymentDate(LocalDate.of(2026, 9, 10))
                .amount(new BigDecimal("15000.00"))
                .currency("ETB")
                .receiptNumber("REC-001")
                .status(PaymentStatus.POSTED)
                .createdBy(testUser)
                .build());
    }

    @Test
    @DisplayName("GET statement returns 401 when unauthenticated")
    void testGetStatementUnauthenticated() throws Exception {
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + halifa.getId() + "/statement"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET statement with date filter calculates opening balance and running balances correctly")
    void testGetStatementWithDateFilter() throws Exception {
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + halifa.getId() + "/statement")
                        .header(HttpHeaders.AUTHORIZATION, financeToken)
                        .param("dateFrom", "2026-09-01")
                        .param("dateTo", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.nonIataCode", is("HAL01")))
                .andExpect(jsonPath("$.data.openingBalance", is(40000.00))) // 50k sales - 10k payments before Sep 1
                .andExpect(jsonPath("$.data.totalSales", is(30000.00)))
                .andExpect(jsonPath("$.data.totalPayments", is(15000.00)))
                .andExpect(jsonPath("$.data.closingBalance", is(55000.00))) // 40k + 30k - 15k
                .andExpect(jsonPath("$.data.transactionCount", is(2)))
                .andExpect(jsonPath("$.data.transactions", hasSize(2)))
                .andExpect(jsonPath("$.data.transactions[0].type", is("SALE")))
                .andExpect(jsonPath("$.data.transactions[0].debit", is(30000.00)))
                .andExpect(jsonPath("$.data.transactions[0].runningBalance", is(70000.00)))
                .andExpect(jsonPath("$.data.transactions[1].type", is("PAYMENT")))
                .andExpect(jsonPath("$.data.transactions[1].credit", is(15000.00)))
                .andExpect(jsonPath("$.data.transactions[1].runningBalance", is(55000.00)));
    }

    @Test
    @DisplayName("GET statement accessible by GENERAL_MANAGER role")
    void testGetStatementGeneralManager() throws Exception {
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + halifa.getId() + "/statement")
                        .header(HttpHeaders.AUTHORIZATION, gmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.nonIataCode", is("HAL01")));
    }

    @Test
    @DisplayName("GET statement returns 400 when dateFrom is after dateTo")
    void testGetStatementInvalidDateRange() throws Exception {
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + halifa.getId() + "/statement")
                        .header(HttpHeaders.AUTHORIZATION, financeToken)
                        .param("dateFrom", "2026-09-30")
                        .param("dateTo", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("GET statement Excel export returns binary spreadsheet")
    void testExportStatementExcel() throws Exception {
        byte[] content = mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + halifa.getId() + "/statement/export/excel")
                        .header(HttpHeaders.AUTHORIZATION, financeToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("statement_HAL01")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(content).isNotEmpty();
        assertThat(content.length).isGreaterThan(500);
    }

    @Test
    @DisplayName("GET statement PDF export returns binary PDF")
    void testExportStatementPdf() throws Exception {
        byte[] content = mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + halifa.getId() + "/statement/export/pdf")
                        .header(HttpHeaders.AUTHORIZATION, gmToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("statement_HAL01")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(content).isNotEmpty();
        assertThat(content.length).isGreaterThan(500);
        // Verify PDF Magic Bytes
        assertThat(new String(content, 0, 5)).isEqualTo("%PDF-");
    }
}
