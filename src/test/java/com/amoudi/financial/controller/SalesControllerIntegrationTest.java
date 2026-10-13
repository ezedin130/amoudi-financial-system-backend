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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalesControllerIntegrationTest {

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
    private NonIata nonIata1;
    private NonIata nonIata2;
    private ImportBatch batch1;
    private SalesTransaction sale1;
    private SalesTransaction sale2;

    @BeforeEach
    void setUp() {
        salesTransactionRepository.deleteAll();
        importBatchRepository.deleteAll();
        nonIataRepository.deleteAll();
        userRepository.deleteAll();

        User financeUser = userRepository.save(User.builder()
                .username("finance_usr")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .fullName("Finance Officer")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        User gmUser = userRepository.save(User.builder()
                .username("gm_usr")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .fullName("General Manager")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        financeToken = jwtService.generateToken("finance_usr", financeUser.getId(), UserRole.FINANCE, 3600000);
        gmToken = jwtService.generateToken("gm_usr", gmUser.getId(), UserRole.GENERAL_MANAGER, 3600000);

        nonIata1 = nonIataRepository.save(NonIata.builder()
                .code("HALIFA")
                .name("Halifa Travel")
                .status(NonIataStatus.ACTIVE)
                .build());

        nonIata2 = nonIataRepository.save(NonIata.builder()
                .code("AGENCY_B")
                .name("Agency B Travel")
                .status(NonIataStatus.ACTIVE)
                .build());

        batch1 = importBatchRepository.save(ImportBatch.builder()
                .fileName("bsp_sales_sep2026.xlsx")
                .objectKey("s3://batches/bsp_sales_sep2026.xlsx")
                .uploadedBy(financeUser)
                .uploadedAt(LocalDateTime.now())
                .status(ImportStatus.IMPORTED)
                .totalRows(2)
                .successfulRows(2)
                .build());

        sale1 = salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(nonIata1)
                .importBatch(batch1)
                .ticketNumber("072-1111111111")
                .transactionReference("TXN-HALIFA-001")
                .saleDate(LocalDate.of(2026, 9, 10))
                .passengerName("Ahmed Ali")
                .origin("JED")
                .destination("ADD")
                .route("JED-ADD")
                .amount(new BigDecimal("200000.00"))
                .currency("ETB")
                .sourceAgencyName("HALIFA TOURS")
                .sourceRowNumber(1)
                .build());

        sale2 = salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(nonIata2)
                .importBatch(batch1)
                .ticketNumber("072-2222222222")
                .transactionReference("TXN-AGENCYB-001")
                .saleDate(LocalDate.of(2026, 9, 15))
                .passengerName("Sara Omar")
                .origin("DXB")
                .destination("ADD")
                .route("DXB-ADD")
                .amount(new BigDecimal("150000.00"))
                .currency("ETB")
                .sourceAgencyName("AGENCY B TOURS")
                .sourceRowNumber(2)
                .build());
    }

    @Test
    @DisplayName("1. Authenticated FINANCE user can list sales (200 OK)")
    void listSales_byFinance_returns200Ok() throws Exception {
        mockMvc.perform(get(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements", is(2)));
    }

    @Test
    @DisplayName("2. Authenticated GENERAL_MANAGER can list sales (200 OK)")
    void listSales_byGeneralManager_returns200Ok() throws Exception {
        mockMvc.perform(get(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalElements", is(2)));
    }

    @Test
    @DisplayName("3. Unauthenticated user cannot list sales (401 Unauthorized)")
    void listSales_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(Constants.SALES_API_PREFIX))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("4. Individual sale can be retrieved (200 OK)")
    void getSaleById_success() throws Exception {
        mockMvc.perform(get(Constants.SALES_API_PREFIX + "/" + sale1.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.ticketNumber", is("072-1111111111")))
                .andExpect(jsonPath("$.data.nonIataCode", is("HALIFA")))
                .andExpect(jsonPath("$.data.amount", is(200000.00)));
    }

    @Test
    @DisplayName("5. Missing sale returns 404 Not Found")
    void getSaleById_notFound() throws Exception {
        mockMvc.perform(get(Constants.SALES_API_PREFIX + "/999999")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("6. Filter by Non-IATA ID works")
    void listSales_filterByNonIataId() throws Exception {
        mockMvc.perform(get(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .param("nonIataId", nonIata1.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].ticketNumber", is("072-1111111111")));
    }

    @Test
    @DisplayName("7. Filter by exact saleDate works")
    void listSales_filterBySaleDate() throws Exception {
        mockMvc.perform(get(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .param("saleDate", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].ticketNumber", is("072-1111111111")));
    }

    @Test
    @DisplayName("8. Filter by date range works")
    void listSales_filterByDateRange() throws Exception {
        mockMvc.perform(get(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .param("startDate", "2026-09-12")
                        .param("endDate", "2026-09-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].ticketNumber", is("072-2222222222")));
    }

    @Test
    @DisplayName("9. Filter by ticket number substring works")
    void listSales_filterByTicketNumber() throws Exception {
        mockMvc.perform(get(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .param("ticketNumber", "111111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].nonIataCode", is("HALIFA")));
    }

    @Test
    @DisplayName("10. Filter by transaction reference works")
    void listSales_filterByTransactionReference() throws Exception {
        mockMvc.perform(get(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .param("transactionReference", "TXN-AGENCYB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].nonIataCode", is("AGENCY_B")));
    }

    @Test
    @DisplayName("11. Filter by import batch ID works")
    void listSales_filterByImportBatchId() throws Exception {
        mockMvc.perform(get(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .param("importBatchId", batch1.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)));
    }

    @Test
    @DisplayName("12. Pagination bounds and page navigation work")
    void listSales_pagination() throws Exception {
        mockMvc.perform(get(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.pageSize", is(1)))
                .andExpect(jsonPath("$.data.totalElements", is(2)))
                .andExpect(jsonPath("$.data.totalPages", is(2)))
                .andExpect(jsonPath("$.data.hasNext", is(true)));
    }

    @Test
    @DisplayName("13. Safe sorting works")
    void listSales_safeSorting() throws Exception {
        mockMvc.perform(get(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .param("sort", "amount,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].amount", is(150000.00)))
                .andExpect(jsonPath("$.data.content[1].amount", is(200000.00)));
    }

    @Test
    @DisplayName("14. Invalid sort property is rejected (400 Bad Request)")
    void listSales_invalidSort_rejected() throws Exception {
        mockMvc.perform(get(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .param("sort", "untrustedSqlField,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("15. Invalid date range (startDate > endDate) is rejected (400 Bad Request)")
    void listSales_invalidDateRange_rejected() throws Exception {
        mockMvc.perform(get(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .param("startDate", "2026-09-30")
                        .param("endDate", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("16. POST /api/v1/sales is strictly prohibited (405 Method Not Allowed)")
    void postSales_prohibited_returns405() throws Exception {
        mockMvc.perform(post(Constants.SALES_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1000}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status", is(405)))
                .andExpect(jsonPath("$.error", is("METHOD_NOT_ALLOWED")));
    }

    @Test
    @DisplayName("17. PUT /api/v1/sales/{id} is strictly prohibited (405 Method Not Allowed)")
    void putSales_prohibited_returns405() throws Exception {
        mockMvc.perform(put(Constants.SALES_API_PREFIX + "/" + sale1.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1000}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status", is(405)))
                .andExpect(jsonPath("$.error", is("METHOD_NOT_ALLOWED")));
    }

    @Test
    @DisplayName("18. DELETE /api/v1/sales/{id} is strictly prohibited (405 Method Not Allowed)")
    void deleteSales_prohibited_returns405() throws Exception {
        mockMvc.perform(delete(Constants.SALES_API_PREFIX + "/" + sale1.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status", is(405)))
                .andExpect(jsonPath("$.error", is("METHOD_NOT_ALLOWED")));
    }
}
