package com.amoudi.financial.acceptance;

import com.amoudi.financial.dto.CreatePaymentRequest;
import com.amoudi.financial.enums.*;
import com.amoudi.financial.model.NameMapping;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.*;
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

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real-World Acceptance Scenario Verification (Part 10 - Pillar 11):
 * Executes the complete real-world chain from authoritative SEP- 17.xlsx report:
 * SEP- 17.xlsx -> 18 transactions -> 5 Non-IATAs -> ETB 1,282,870.00 sales
 * -> payments -> balances -> statements -> aging -> exports.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RealWorldAcceptanceScenarioIntegrationTest {

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

    private User financeUser;
    private User gmUser;
    private String financeToken;
    private String gmToken;

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

        financeUser = userRepository.save(User.builder()
                .username("accept_finance")
                .passwordHash(passwordEncoder.encode("Pass@12345"))
                .fullName("Finance Officer")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        gmUser = userRepository.save(User.builder()
                .username("accept_gm")
                .passwordHash(passwordEncoder.encode("Pass@12345"))
                .fullName("General Manager")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        financeToken = jwtService.generateToken(financeUser.getUsername(), financeUser.getId(), UserRole.FINANCE, 3600000);
        gmToken = jwtService.generateToken(gmUser.getUsername(), gmUser.getId(), UserRole.GENERAL_MANAGER, 3600000);

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

    @Test
    @DisplayName("Real-world scenario: Full lifecycle execution of SEP- 17.xlsx with multiple payments and report reconciliation")
    void testRealWorldAcceptanceExecution() throws Exception {
        // 1. Upload SEP- 17.xlsx
        byte[] realExcelBytes;
        try (InputStream is = getClass().getResourceAsStream("/fixtures/SEP- 17.xlsx")) {
            assertThat(is).isNotNull();
            realExcelBytes = is.readAllBytes();
        }

        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "SEP- 17.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                realExcelBytes
        );

        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(multipartFile)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("IMPORTED")))
                .andExpect(jsonPath("$.data.successfulRows", is(18)));

        assertThat(salesTransactionRepository.count()).isEqualTo(18L);

        // 2. Verify individual initial balances
        // Halifa: 119,842.00
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + halifa.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outstandingBalance", is(119842.00)));

        // Emran: 822,008.00
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + emran.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outstandingBalance", is(822008.00)));

        // Feysel: 149,456.00
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + feysel.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outstandingBalance", is(149456.00)));

        // 3. Post Payments by GM:
        // Halifa: pays in full 119,842.00
        CreatePaymentRequest payHalifa = CreatePaymentRequest.builder()
                .nonIataId(halifa.getId())
                .amount(new BigDecimal("119842.00"))
                .paymentDate(LocalDate.now())
                .paymentReference("PAY-HAL-FULL")
                .build();
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payHalifa))
                .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isCreated());

        // Emran: pays 300,000.00
        CreatePaymentRequest payEmran = CreatePaymentRequest.builder()
                .nonIataId(emran.getId())
                .amount(new BigDecimal("300000.00"))
                .paymentDate(LocalDate.now())
                .paymentReference("PAY-EMR-PART")
                .build();
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payEmran))
                .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isCreated());

        // Feysel: pays 50,000.00
        CreatePaymentRequest payFeysel = CreatePaymentRequest.builder()
                .nonIataId(feysel.getId())
                .amount(new BigDecimal("50000.00"))
                .paymentDate(LocalDate.now())
                .paymentReference("PAY-FEY-PART")
                .build();
        mockMvc.perform(post(Constants.PAYMENTS_API_PREFIX)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payFeysel))
                .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isCreated());

        // 4. Verify Post-Payment Balances:
        // Halifa: 119,842.00 - 119,842.00 = 0.00
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + halifa.getId())
                .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outstandingBalance", is(0.00)));

        // Emran: 822,008.00 - 300,000.00 = 522,008.00
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + emran.getId())
                .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outstandingBalance", is(522008.00)));

        // Feysel: 149,456.00 - 50,000.00 = 99,456.00
        mockMvc.perform(get(Constants.FINANCIAL_SUMMARIES_API_PREFIX + "/" + feysel.getId())
                .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outstandingBalance", is(99456.00)));

        // 5. Total company outstanding balance:
        // Total Sales: 1,282,870.00
        // Total Payments: 469,842.00
        // Balance: 813,028.00 ETB
        mockMvc.perform(get(Constants.AGING_API_PREFIX)
                .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOutstanding", is(813028.00)));

        // 6. Verify Exports succeed with HTTP 200
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + halifa.getId() + "/statement/export/excel")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + emran.getId() + "/statement/export/pdf")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(Constants.AGING_API_PREFIX + "/export/excel")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(Constants.AGING_API_PREFIX + "/export/pdf")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk());
    }
}
