package com.amoudi.financial.concurrency;

import com.amoudi.financial.dto.CreatePaymentRequest;
import com.amoudi.financial.dto.PaymentResponse;
import com.amoudi.financial.enums.AuditAction;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.PaymentStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.exception.BadRequestException;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.Payment;
import com.amoudi.financial.model.SalesTransaction;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.AuditLogRepository;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.PaymentRepository;
import com.amoudi.financial.repository.SalesTransactionRepository;
import com.amoudi.financial.repository.UserRepository;
import com.amoudi.financial.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConcurrencySafetyTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SalesTransactionRepository salesTransactionRepository;

    @Autowired
    private com.amoudi.financial.repository.ImportBatchRepository importBatchRepository;

    @Autowired
    private NonIataRepository nonIataRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User financeUser;
    private User gmUser;
    private NonIata nonIata;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        salesTransactionRepository.deleteAll();
        paymentRepository.deleteAll();
        nonIataRepository.deleteAll();
        userRepository.deleteAll();

        financeUser = userRepository.save(User.builder()
                .username("finance_concurrency")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .fullName("Finance Concurrency")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        gmUser = userRepository.save(User.builder()
                .username("gm_concurrency")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .fullName("GM Concurrency")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        nonIata = nonIataRepository.save(NonIata.builder()
                .code("CONCUR01")
                .name("Concurrency Travel")
                .status(NonIataStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("Subsequent or concurrent cancellation of an already CANCELLED payment is rejected with 400 Bad Request")
    void doublePaymentCancellation_rejectsSecondWithBadRequest() {
        // Create initial POSTED payment
        PaymentResponse payment = paymentService.createPayment(CreatePaymentRequest.builder()
                .nonIataId(nonIata.getId())
                .amount(new BigDecimal("15000.00"))
                .paymentDate(LocalDate.now())
                .build(), gmUser.getUsername());

        // First cancellation succeeds
        PaymentResponse firstResult = paymentService.cancelPayment(payment.getId(), financeUser.getUsername());
        assertThat(firstResult.getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        // Second cancellation must throw BadRequestException
        assertThatThrownBy(() -> paymentService.cancelPayment(payment.getId(), financeUser.getUsername()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("is already CANCELLED");

        // Verify exactly one PAYMENT_CANCEL audit record was written
        var cancelLogs = auditLogRepository.findByAction(AuditAction.PAYMENT_CANCEL);
        long matchingLogs = cancelLogs.stream()
                .filter(l -> payment.getId().equals(l.getEntityId()))
                .count();

        assertThat(matchingLogs).isEqualTo(1L);
    }

    @Test
    @DisplayName("Duplicate sales transaction key is rejected authoritatively by database unique constraint")
    void duplicateSalesTransactionKey_rejectedByUniqueConstraint() {
        String uniqueKey = "TX-KEY-AUTHORITATIVE-001";

        com.amoudi.financial.model.ImportBatch batch = importBatchRepository.save(com.amoudi.financial.model.ImportBatch.builder()
                .fileName("test_sales.xlsx")
                .objectKey("imports/test_sales.xlsx")
                .uploadedBy(financeUser)
                .uploadedAt(java.time.LocalDateTime.now())
                .status(com.amoudi.financial.enums.ImportStatus.IMPORTED)
                .build());

        SalesTransaction tx1 = SalesTransaction.builder()
                .nonIata(nonIata)
                .importBatch(batch)
                .ticketNumber("001-1234567890")
                .transactionReference("PNR001")
                .saleDate(LocalDate.now())
                .amount(new BigDecimal("5000.00"))
                .currency("ETB")
                .sourceAgencyName("TEST AGENCY")
                .sourceRowNumber(10)
                .sourceTransactionKey(uniqueKey)
                .build();

        salesTransactionRepository.saveAndFlush(tx1);

        SalesTransaction tx2 = SalesTransaction.builder()
                .nonIata(nonIata)
                .importBatch(batch)
                .ticketNumber("001-9876543210")
                .transactionReference("PNR002")
                .saleDate(LocalDate.now())
                .amount(new BigDecimal("3500.00"))
                .currency("ETB")
                .sourceAgencyName("TEST AGENCY")
                .sourceRowNumber(15)
                .sourceTransactionKey(uniqueKey) // Duplicate unique key
                .build();

        assertThatThrownBy(() -> salesTransactionRepository.saveAndFlush(tx2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
