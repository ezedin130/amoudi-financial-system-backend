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
class AgingControllerIntegrationTest {

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
    private User testUser;
    private NonIata halifa;
    private Payment payment1;
    private ImportBatch batch;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
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
                .username("gm_executive")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .fullName("General Manager Executive")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        financeToken = Constants.TOKEN_BEARER_PREFIX + jwtService.generateToken(testUser.getUsername(), testUser.getId(), testUser.getRole(), 3600000L);
        gmToken = Constants.TOKEN_BEARER_PREFIX + jwtService.generateToken(gmUser.getUsername(), gmUser.getId(), gmUser.getRole(), 3600000L);

        batch = importBatchRepository.save(ImportBatch.builder()
                .fileName("test_sales.xlsx")
                .objectKey("s3://batches/test_sales.xlsx")
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

        LocalDate today = LocalDate.now();

        // 600,000 ETB total sales across 3 distinct aging buckets:
        // Sale 1: 150 days ago -> 100,000 ETB (120+ days bucket)
        salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(halifa)
                .importBatch(batch)
                .ticketNumber("T-101")
                .passengerName("Passenger 1")
                .saleDate(today.minusDays(150))
                .amount(new BigDecimal("100000.00"))
                .currency("ETB")
                .sourceAgencyName("HALIFA")
                .sourceRowNumber(1)
                .build());

        // Sale 2: 45 days ago -> 200,000 ETB (31-60 days bucket)
        salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(halifa)
                .importBatch(batch)
                .ticketNumber("T-102")
                .passengerName("Passenger 2")
                .saleDate(today.minusDays(45))
                .amount(new BigDecimal("200000.00"))
                .currency("ETB")
                .sourceAgencyName("HALIFA")
                .sourceRowNumber(2)
                .build());

        // Sale 3: 10 days ago -> 300,000 ETB (0-30 days / Current bucket)
        salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(halifa)
                .importBatch(batch)
                .ticketNumber("T-103")
                .passengerName("Passenger 3")
                .saleDate(today.minusDays(10))
                .amount(new BigDecimal("300000.00"))
                .currency("ETB")
                .sourceAgencyName("HALIFA")
                .sourceRowNumber(3)
                .build());

        // Posted Payment of 250,000 ETB
        payment1 = paymentRepository.save(Payment.builder()
                .nonIata(halifa)
                .paymentDate(today.minusDays(5))
                .amount(new BigDecimal("250000.00"))
                .currency("ETB")
                .receiptNumber("REC-HAL-01")
                .paymentReference("BANK-TRANSFER-999")
                .status(PaymentStatus.POSTED)
                .createdBy(testUser)
                .build());
    }

    @Test
    @DisplayName("Critical Acceptance Test: FIFO Aging distributes 250k payment against 600k sales, and cancellation updates buckets")
    void testCriticalAcceptanceAgingAndCancellation() throws Exception {
        LocalDate today = LocalDate.now();

        // 1. Initial State: 600,000 sales - 250,000 payment = 350,000 balance
        // Oldest sale (100k) settled, middle sale (200k) has 50k left, newest sale (300k) unpaid
        mockMvc.perform(get(Constants.AGING_API_PREFIX)
                        .header(HttpHeaders.AUTHORIZATION, financeToken)
                        .param("asOfDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalAccounts", is(1)))
                .andExpect(jsonPath("$.data.totalOutstanding", is(350000.00)))
                .andExpect(jsonPath("$.data.totalCurrent", is(300000.00)))
                .andExpect(jsonPath("$.data.totalDays31To60", is(50000.00)))
                .andExpect(jsonPath("$.data.totalDays61To90", is(0.00)))
                .andExpect(jsonPath("$.data.totalDays91To120", is(0.00)))
                .andExpect(jsonPath("$.data.totalDays120Plus", is(0.00)))
                .andExpect(jsonPath("$.data.rows[0].code", is("HAL01")))
                .andExpect(jsonPath("$.data.rows[0].current", is(300000.00)))
                .andExpect(jsonPath("$.data.rows[0].days31To60", is(50000.00)))
                .andExpect(jsonPath("$.data.rows[0].days120Plus", is(0.00)))
                .andExpect(jsonPath("$.data.rows[0].totalOutstanding", is(350000.00)));

        // 2. Cancel the 250,000 ETB payment
        payment1.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.save(payment1);

        // 3. Post-cancellation: Outstanding balance reverts to full 600,000 ETB
        // Buckets revert: Current = 300k, 31-60 = 200k, 120+ = 100k!
        mockMvc.perform(get(Constants.AGING_API_PREFIX)
                        .header(HttpHeaders.AUTHORIZATION, gmToken)
                        .param("asOfDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalOutstanding", is(600000.00)))
                .andExpect(jsonPath("$.data.totalCurrent", is(300000.00)))
                .andExpect(jsonPath("$.data.totalDays31To60", is(200000.00)))
                .andExpect(jsonPath("$.data.totalDays61To90", is(0.00)))
                .andExpect(jsonPath("$.data.totalDays91To120", is(0.00)))
                .andExpect(jsonPath("$.data.totalDays120Plus", is(100000.00)))
                .andExpect(jsonPath("$.data.rows[0].current", is(300000.00)))
                .andExpect(jsonPath("$.data.rows[0].days31To60", is(200000.00)))
                .andExpect(jsonPath("$.data.rows[0].days120Plus", is(100000.00)))
                .andExpect(jsonPath("$.data.rows[0].totalOutstanding", is(600000.00)));
    }

    @Test
    @DisplayName("GET aging returns 401 when unauthenticated")
    void testGetAgingUnauthenticated() throws Exception {
        mockMvc.perform(get(Constants.AGING_API_PREFIX))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET aging Excel export produces valid binary output")
    void testExportAgingExcel() throws Exception {
        byte[] content = mockMvc.perform(get(Constants.AGING_API_PREFIX + "/export/excel")
                        .header(HttpHeaders.AUTHORIZATION, financeToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("aging_report_")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(content).isNotEmpty();
        assertThat(content.length).isGreaterThan(500);
    }

    @Test
    @DisplayName("GET aging PDF export produces valid binary PDF")
    void testExportAgingPdf() throws Exception {
        byte[] content = mockMvc.perform(get(Constants.AGING_API_PREFIX + "/export/pdf")
                        .header(HttpHeaders.AUTHORIZATION, gmToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("aging_report_")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(content).isNotEmpty();
        assertThat(content.length).isGreaterThan(500);
        assertThat(new String(content, 0, 5)).isEqualTo("%PDF-");
    }
}
