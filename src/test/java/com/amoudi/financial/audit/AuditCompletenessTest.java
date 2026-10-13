package com.amoudi.financial.audit;

import com.amoudi.financial.dto.CreatePaymentRequest;
import com.amoudi.financial.dto.LoginRequest;
import com.amoudi.financial.dto.NonIataCreateRequest;
import com.amoudi.financial.dto.NonIataStatusUpdateRequest;
import com.amoudi.financial.dto.NonIataUpdateRequest;
import com.amoudi.financial.dto.PaymentResponse;
import com.amoudi.financial.enums.AuditAction;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.AuditLog;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.Payment;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.AuditLogRepository;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.PaymentRepository;
import com.amoudi.financial.repository.UserRepository;
import com.amoudi.financial.service.AuthService;
import com.amoudi.financial.service.NonIataService;
import com.amoudi.financial.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditCompletenessTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NonIataRepository nonIataRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private NonIataService nonIataService;

    @Autowired
    private PaymentService paymentService;

    private User financeUser;
    private User gmUser;
    private NonIata sampleNonIata;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        paymentRepository.deleteAll();
        nonIataRepository.deleteAll();
        userRepository.deleteAll();

        financeUser = userRepository.save(User.builder()
                .username("finance_audit_tester")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .fullName("Finance Officer")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        gmUser = userRepository.save(User.builder()
                .username("gm_audit_tester")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .fullName("General Manager")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        sampleNonIata = nonIataRepository.save(NonIata.builder()
                .code("AUDIT01")
                .name("Audit Test Travel")
                .status(NonIataStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("User login creates an immutable LOGIN audit record")
    void login_createsAuditRecord() {
        authService.login(LoginRequest.builder()
                .username("finance_audit_tester")
                .password("Secret123!")
                .build());

        List<AuditLog> logs = auditLogRepository.findByAction(AuditAction.LOGIN);
        assertThat(logs).isNotEmpty();

        AuditLog log = logs.stream()
                .filter(l -> l.getUser() != null && l.getUser().getId().equals(financeUser.getId()))
                .findFirst()
                .orElse(null);

        assertThat(log).isNotNull();
        assertThat(log.getEntityType()).isEqualTo("User");
        assertThat(log.getEntityId()).isEqualTo(financeUser.getId());
        assertThat(log.getNewValue()).contains("finance_audit_tester");
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Non-IATA creation generates a CREATE audit record")
    void createNonIata_createsAuditRecord() {
        nonIataService.createNonIata(NonIataCreateRequest.builder()
                .code("NEWAGY")
                .name("New Agency Ltd")
                .build());

        List<AuditLog> logs = auditLogRepository.findByAction(AuditAction.CREATE);
        assertThat(logs).isNotEmpty();

        AuditLog log = logs.stream()
                .filter(l -> "NonIata".equals(l.getEntityType()))
                .findFirst()
                .orElse(null);

        assertThat(log).isNotNull();
        assertThat(log.getNewValue()).contains("NEWAGY");
    }

    @Test
    @DisplayName("Non-IATA update generates an UPDATE audit record with state changes")
    void updateNonIata_createsAuditRecord() {
        nonIataService.updateNonIata(sampleNonIata.getId(), NonIataUpdateRequest.builder()
                .name("Updated Agency Name")
                .contactName("John Doe")
                .phone("+251911000000")
                .email("info@updated.com")
                .notes("Updated notes")
                .build());

        List<AuditLog> logs = auditLogRepository.findByAction(AuditAction.UPDATE);
        assertThat(logs).isNotEmpty();

        AuditLog log = logs.stream()
                .filter(l -> "NonIata".equals(l.getEntityType()) && sampleNonIata.getId().equals(l.getEntityId()))
                .findFirst()
                .orElse(null);

        assertThat(log).isNotNull();
        assertThat(log.getOldValue()).contains("Audit Test Travel");
        assertThat(log.getNewValue()).contains("Updated Agency Name");
    }

    @Test
    @DisplayName("Non-IATA status update generates an UPDATE audit record with status transition")
    void updateNonIataStatus_createsAuditRecord() {
        nonIataService.updateStatus(sampleNonIata.getId(), NonIataStatusUpdateRequest.builder()
                .status(NonIataStatus.INACTIVE)
                .build());

        List<AuditLog> logs = auditLogRepository.findByAction(AuditAction.UPDATE);
        assertThat(logs).isNotEmpty();

        AuditLog log = logs.stream()
                .filter(l -> "NonIata".equals(l.getEntityType()) && l.getNewValue().contains("INACTIVE"))
                .findFirst()
                .orElse(null);

        assertThat(log).isNotNull();
        assertThat(log.getOldValue()).contains("ACTIVE");
        assertThat(log.getNewValue()).contains("INACTIVE");
    }

    @Test
    @DisplayName("Payment creation generates a PAYMENT audit record linking GM actor")
    void createPayment_createsAuditRecord() {
        PaymentResponse response = paymentService.createPayment(CreatePaymentRequest.builder()
                .nonIataId(sampleNonIata.getId())
                .amount(new BigDecimal("25000.00"))
                .currency("ETB")
                .paymentDate(LocalDate.now())
                .receiptNumber("REC-AUDIT-001")
                .paymentReference("BANK-REF-999")
                .build(), gmUser.getUsername());

        List<AuditLog> logs = auditLogRepository.findByAction(AuditAction.PAYMENT);
        assertThat(logs).isNotEmpty();

        AuditLog log = logs.stream()
                .filter(l -> response.getId().equals(l.getEntityId()))
                .findFirst()
                .orElse(null);

        assertThat(log).isNotNull();
        assertThat(log.getUser().getId()).isEqualTo(gmUser.getId());
        assertThat(log.getNewValue()).contains("25000.00");
    }

    @Test
    @DisplayName("Payment cancellation generates a PAYMENT_CANCEL audit record linking Finance actor")
    void cancelPayment_createsAuditRecord() {
        PaymentResponse payment = paymentService.createPayment(CreatePaymentRequest.builder()
                .nonIataId(sampleNonIata.getId())
                .amount(new BigDecimal("10000.00"))
                .paymentDate(LocalDate.now())
                .build(), gmUser.getUsername());

        paymentService.cancelPayment(payment.getId(), financeUser.getUsername());

        List<AuditLog> logs = auditLogRepository.findByAction(AuditAction.PAYMENT_CANCEL);
        assertThat(logs).isNotEmpty();

        AuditLog log = logs.stream()
                .filter(l -> payment.getId().equals(l.getEntityId()))
                .findFirst()
                .orElse(null);

        assertThat(log).isNotNull();
        assertThat(log.getUser().getId()).isEqualTo(financeUser.getId());
        assertThat(log.getOldValue()).contains("status: POSTED");
        assertThat(log.getNewValue()).contains("status: CANCELLED");
    }

    @Test
    @DisplayName("Receipt attachment generates a FILE_UPLOAD audit record")
    void attachReceipt_createsAuditRecord() {
        PaymentResponse payment = paymentService.createPayment(CreatePaymentRequest.builder()
                .nonIataId(sampleNonIata.getId())
                .amount(new BigDecimal("5000.00"))
                .paymentDate(LocalDate.now())
                .build(), gmUser.getUsername());

        MockMultipartFile receiptFile = new MockMultipartFile(
                "file",
                "bank_deposit.pdf",
                "application/pdf",
                "%PDF-1.4 authoritative test receipt content".getBytes()
        );

        paymentService.attachReceipt(payment.getId(), receiptFile, gmUser.getUsername());

        List<AuditLog> logs = auditLogRepository.findByAction(AuditAction.FILE_UPLOAD);
        assertThat(logs).isNotEmpty();

        AuditLog log = logs.stream()
                .filter(l -> "Payment".equals(l.getEntityType()) && payment.getId().equals(l.getEntityId()))
                .findFirst()
                .orElse(null);

        assertThat(log).isNotNull();
        assertThat(log.getUser().getId()).isEqualTo(gmUser.getId());
        assertThat(log.getNewValue()).contains("Attached receipt_object_key");
    }

    @Test
    @DisplayName("Receipt download generates a FILE_DOWNLOAD audit record")
    void downloadReceipt_createsAuditRecord() {
        paymentService.recordReceiptDownloadAudit(100L, financeUser.getUsername());

        List<AuditLog> logs = auditLogRepository.findByAction(AuditAction.FILE_DOWNLOAD);
        assertThat(logs).isNotEmpty();

        AuditLog log = logs.stream()
                .filter(l -> Long.valueOf(100L).equals(l.getEntityId()))
                .findFirst()
                .orElse(null);

        assertThat(log).isNotNull();
        assertThat(log.getUser().getId()).isEqualTo(financeUser.getId());
        assertThat(log.getNewValue()).contains("payment id=100");
    }
}
