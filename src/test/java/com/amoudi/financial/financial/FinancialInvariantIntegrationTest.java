package com.amoudi.financial.financial;

import com.amoudi.financial.dto.AccountStatementResponse;
import com.amoudi.financial.dto.AgingReportResponse;
import com.amoudi.financial.dto.AgingRowResponse;
import com.amoudi.financial.dto.FinancialSummaryResponse;
import com.amoudi.financial.enums.ImportStatus;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.PaymentStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.*;
import com.amoudi.financial.repository.*;
import com.amoudi.financial.service.AgingService;
import com.amoudi.financial.service.BalanceService;
import com.amoudi.financial.service.StatementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Financial Invariant Verification Test (Part 10 - Pillar 5):
 * Proves that under all scenarios:
 * Outstanding Balance = Total Imported Sales - Total Posted Payments
 * holds identically across:
 * 1. Financial Summary
 * 2. Account Statement
 * 3. Aging Report
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FinancialInvariantIntegrationTest {

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private StatementService statementService;

    @Autowired
    private AgingService agingService;

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

    private User financeUser;
    private User gmUser;
    private ImportBatch batch;
    private NonIata agency1;
    private NonIata agency2;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        salesTransactionRepository.deleteAll();
        importBatchRepository.deleteAll();
        nonIataRepository.deleteAll();
        userRepository.deleteAll();

        financeUser = userRepository.save(User.builder()
                .username("fin_inv_test")
                .passwordHash("hashed")
                .fullName("Finance Officer")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        gmUser = userRepository.save(User.builder()
                .username("gm_inv_test")
                .passwordHash("hashed")
                .fullName("General Manager")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        batch = importBatchRepository.save(ImportBatch.builder()
                .fileName("test_batch.xlsx")
                .objectKey("imports/test_batch_" + java.util.UUID.randomUUID())
                .uploadedBy(financeUser)
                .status(ImportStatus.IMPORTED)
                .build());

        agency1 = nonIataRepository.save(NonIata.builder().code("INV_AG1").name("Invariant Agency 1").status(NonIataStatus.ACTIVE).build());
        agency2 = nonIataRepository.save(NonIata.builder().code("INV_AG2").name("Invariant Agency 2").status(NonIataStatus.ACTIVE).build());
    }

    private void recordSale(NonIata agency, String ticket, BigDecimal amount, LocalDate date) {
        salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(agency)
                .importBatch(batch)
                .ticketNumber(ticket)
                .amount(amount)
                .currency("ETB")
                .saleDate(date)
                .sourceAgencyName("TEST_AGENCY")
                .sourceRowNumber(1)
                .sourceTransactionKey("TX_" + ticket)
                .build());
    }

    private Payment recordPayment(NonIata agency, BigDecimal amount, PaymentStatus status, LocalDate date) {
        return paymentRepository.save(Payment.builder()
                .nonIata(agency)
                .amount(amount)
                .currency("ETB")
                .paymentDate(date)
                .paymentReference("INV-REF")
                .status(status)
                .createdBy(gmUser)
                .build());
    }

    private void assertFinancialInvariantReconciled(NonIata agency, BigDecimal expectedSales, BigDecimal expectedPayments, BigDecimal expectedBalance) {
        // 1. Check Financial Summary
        FinancialSummaryResponse summary = balanceService.getFinancialSummary(agency.getId());
        assertThat(summary.getTotalSales()).isEqualByComparingTo(expectedSales);
        assertThat(summary.getTotalPayments()).isEqualByComparingTo(expectedPayments);
        assertThat(summary.getOutstandingBalance()).isEqualByComparingTo(expectedBalance);

        // 2. Check Statement
        AccountStatementResponse statement = statementService.getStatement(agency.getId(), null, null);
        assertThat(statement.getTotalSales()).isEqualByComparingTo(expectedSales);
        assertThat(statement.getTotalPayments()).isEqualByComparingTo(expectedPayments);
        assertThat(statement.getClosingBalance()).isEqualByComparingTo(expectedBalance);

        // 3. Check Aging Report
        AgingReportResponse agingReport = agingService.getAgingReport(LocalDate.now(), null, null, null);
        AgingRowResponse agencyAging = agingReport.getRows().stream()
                .filter(r -> r.getNonIataId().equals(agency.getId()))
                .findFirst()
                .orElse(null);

        if (expectedBalance.compareTo(BigDecimal.ZERO) != 0 || expectedSales.compareTo(BigDecimal.ZERO) != 0) {
            assertThat(agencyAging).isNotNull();
            assertThat(agencyAging.getTotalOutstanding()).isEqualByComparingTo(expectedBalance);
        }

        // Cross-representation direct equality
        assertThat(summary.getOutstandingBalance()).isEqualByComparingTo(statement.getClosingBalance());
        if (agencyAging != null) {
            assertThat(statement.getClosingBalance()).isEqualByComparingTo(agencyAging.getTotalOutstanding());
        }
    }

    @Test
    @DisplayName("Scenario 1: Zero sales and zero payments produces 0.00 across all views")
    void testZeroSalesAndZeroPayments() {
        assertFinancialInvariantReconciled(agency1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Scenario 2: Sales with zero payments: Outstanding = Total Sales")
    void testSalesOnly() {
        recordSale(agency1, "TKT-001", new BigDecimal("50000.00"), LocalDate.now().minusDays(5));
        recordSale(agency1, "TKT-002", new BigDecimal("35000.00"), LocalDate.now().minusDays(3));

        assertFinancialInvariantReconciled(agency1, new BigDecimal("85000.00"), BigDecimal.ZERO, new BigDecimal("85000.00"));
    }

    @Test
    @DisplayName("Scenario 3: Multiple sales and multiple posted payments: Outstanding = Sales - Sum(Payments)")
    void testSalesWithMultiplePayments() {
        recordSale(agency1, "TKT-001", new BigDecimal("60000.00"), LocalDate.now().minusDays(10));
        recordSale(agency1, "TKT-002", new BigDecimal("40000.00"), LocalDate.now().minusDays(8));

        recordPayment(agency1, new BigDecimal("30000.00"), PaymentStatus.POSTED, LocalDate.now().minusDays(5));
        recordPayment(agency1, new BigDecimal("25000.00"), PaymentStatus.POSTED, LocalDate.now().minusDays(2));

        // Sales = 100,000, Payments = 55,000, Outstanding = 45,000
        assertFinancialInvariantReconciled(agency1, new BigDecimal("100000.00"), new BigDecimal("55000.00"), new BigDecimal("45000.00"));
    }

    @Test
    @DisplayName("Scenario 4: Cancelled payments are strictly excluded from balance calculation")
    void testCancelledPaymentsExcluded() {
        recordSale(agency1, "TKT-001", new BigDecimal("70000.00"), LocalDate.now().minusDays(5));

        recordPayment(agency1, new BigDecimal("20000.00"), PaymentStatus.POSTED, LocalDate.now().minusDays(4));
        recordPayment(agency1, new BigDecimal("30000.00"), PaymentStatus.CANCELLED, LocalDate.now().minusDays(3)); // Cancelled

        // Sales = 70,000, Payments = 20,000 (30,000 excluded), Outstanding = 50,000
        assertFinancialInvariantReconciled(agency1, new BigDecimal("70000.00"), new BigDecimal("20000.00"), new BigDecimal("50000.00"));
    }

    @Test
    @DisplayName("Scenario 5: Exact full payment reduces balance to exactly 0.00")
    void testExactFullPayment() {
        recordSale(agency1, "TKT-001", new BigDecimal("45000.00"), LocalDate.now().minusDays(5));
        recordPayment(agency1, new BigDecimal("45000.00"), PaymentStatus.POSTED, LocalDate.now().minusDays(2));

        assertFinancialInvariantReconciled(agency1, new BigDecimal("45000.00"), new BigDecimal("45000.00"), BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Scenario 6: Multiple independent Non-IATAs reconcile individually and in company aggregate")
    void testMultipleAgenciesReconcileIndependently() {
        // Agency 1
        recordSale(agency1, "TKT-A1", new BigDecimal("100000.00"), LocalDate.now().minusDays(10));
        recordPayment(agency1, new BigDecimal("40000.00"), PaymentStatus.POSTED, LocalDate.now().minusDays(5));

        // Agency 2
        recordSale(agency2, "TKT-B1", new BigDecimal("80000.00"), LocalDate.now().minusDays(7));
        recordPayment(agency2, new BigDecimal("50000.00"), PaymentStatus.POSTED, LocalDate.now().minusDays(3));

        // Check Agency 1 (100k - 40k = 60k)
        assertFinancialInvariantReconciled(agency1, new BigDecimal("100000.00"), new BigDecimal("40000.00"), new BigDecimal("60000.00"));

        // Check Agency 2 (80k - 50k = 30k)
        assertFinancialInvariantReconciled(agency2, new BigDecimal("80000.00"), new BigDecimal("50000.00"), new BigDecimal("30000.00"));

        // Check Company Aging Grand Total = 60k + 30k = 90k
        AgingReportResponse companyAging = agingService.getAgingReport(LocalDate.now(), null, null, null);
        assertThat(companyAging.getTotalOutstanding()).isEqualByComparingTo(new BigDecimal("90000.00"));
    }
}
