package com.amoudi.financial.concurrency;

import com.amoudi.financial.dto.CreatePaymentRequest;
import com.amoudi.financial.dto.FinancialSummaryResponse;
import com.amoudi.financial.dto.PaymentResponse;
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
import com.amoudi.financial.repository.PaymentRepository;
import com.amoudi.financial.repository.SalesTransactionRepository;
import com.amoudi.financial.repository.UserRepository;
import com.amoudi.financial.service.BalanceService;
import com.amoudi.financial.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency Testing (Part 10 - Pillar 8):
 * Simulates concurrent multi-threaded payment creation on the same Non-IATA account
 * and verifies thread safety, absence of deadlocks, and exact financial balance reconciliation.
 */
@SpringBootTest
@ActiveProfiles("test")
public class ConcurrentPaymentExecutionIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SalesTransactionRepository salesTransactionRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private NonIataRepository nonIataRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User gmUser;
    private NonIata agency;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        salesTransactionRepository.deleteAll();
        importBatchRepository.deleteAll();
        nonIataRepository.deleteAll();
        userRepository.deleteAll();

        gmUser = userRepository.save(User.builder()
                .username("gm_concurrency_thread")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .fullName("GM Concurrency")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        agency = nonIataRepository.save(NonIata.builder()
                .code("CONCUR_MULTI")
                .name("Multi-Threaded Travel")
                .status(NonIataStatus.ACTIVE)
                .build());

        ImportBatch batch = importBatchRepository.save(ImportBatch.builder()
                .fileName("concurrency_sales.xlsx")
                .objectKey("imports/test_concur_" + UUID.randomUUID())
                .uploadedBy(gmUser)
                .status(ImportStatus.IMPORTED)
                .build());

        // Initial sales: 20,000.00 ETB
        salesTransactionRepository.save(SalesTransaction.builder()
                .nonIata(agency)
                .importBatch(batch)
                .ticketNumber("065 1111111111")
                .saleDate(LocalDate.now().minusDays(5))
                .passengerName("TEST/CONCUR")
                .amount(new BigDecimal("20000.00"))
                .currency("ETB")
                .sourceAgencyName("Multi-Threaded Travel")
                .sourceRowNumber(1)
                .build());
    }

    @AfterEach
    void tearDown() {
        paymentRepository.deleteAll();
        salesTransactionRepository.deleteAll();
        importBatchRepository.deleteAll();
        nonIataRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Pillar 8: 10 concurrent payment creations on the same account execute safely and reconcile balance")
    void testConcurrentPaymentCreations() throws InterruptedException {
        int threadCount = 10;
        BigDecimal paymentPerThread = new BigDecimal("1000.00");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Synchronize all threads to fire simultaneously
                    PaymentResponse resp = paymentService.createPayment(
                            CreatePaymentRequest.builder()
                                    .nonIataId(agency.getId())
                                    .amount(paymentPerThread)
                                    .paymentDate(LocalDate.now())
                                    .paymentReference("CONCUR-PAY-" + index)
                                    .receiptNumber("REC-CONCUR-" + index)
                                    .build(),
                            gmUser.getUsername()
                    );
                    if (resp != null && resp.getId() != null) {
                        successCounter.incrementAndGet();
                    }
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Wait for all threads to be ready
        readyLatch.await(5, TimeUnit.SECONDS);
        // Unleash all threads concurrently
        startLatch.countDown();
        // Wait for completion
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("All concurrent tasks should complete within timeout").isTrue();
        assertThat(exceptions).as("No threads should encounter exceptions or deadlocks").isEmpty();
        assertThat(successCounter.get()).isEqualTo(threadCount);

        // Verify database state: exactly 10 payments created
        long totalPaymentsInDb = paymentRepository.count();
        assertThat(totalPaymentsInDb).isEqualTo(threadCount);

        // Verify financial balance reconciliation:
        // Outstanding Balance = 20,000.00 (Sales) - 10 * 1,000.00 (Payments) = 10,000.00 ETB
        FinancialSummaryResponse summary = balanceService.getFinancialSummary(agency.getId());
        assertThat(summary.getTotalSales()).isEqualByComparingTo(new BigDecimal("20000.00"));
        assertThat(summary.getTotalPayments()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(summary.getOutstandingBalance()).isEqualByComparingTo(new BigDecimal("10000.00"));
    }
}
