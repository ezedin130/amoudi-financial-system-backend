package com.amoudi.financial.repository;

import com.amoudi.financial.enums.AuditAction;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.AuditLog;
import com.amoudi.financial.model.ImportBatch;
import com.amoudi.financial.model.NameMapping;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.Payment;
import com.amoudi.financial.model.SalesTransaction;
import com.amoudi.financial.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseSchemaIntegrationTest {

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
    private NameMappingRepository nameMappingRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    @DisplayName("Should successfully insert and retrieve a User entity with BIGINT generated ID")
    void shouldPersistAndRetrieveUser() {
        User user = User.builder()
                .username("finance_tester")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .fullName("Finance Test User")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build();

        User saved = userRepository.saveAndFlush(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isPositive();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<User> found = userRepository.findByUsername("finance_tester");
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Finance Test User");
    }

    @Test
    @Transactional
    @DisplayName("Should enforce UNIQUE constraint on username")
    void shouldEnforceUniqueUsername() {
        User user1 = User.builder()
                .username("unique_user")
                .passwordHash("hash1")
                .fullName("User One")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.saveAndFlush(user1);

        User user2 = User.builder()
                .username("unique_user")
                .passwordHash("hash2")
                .fullName("User Two")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("Should enforce UNIQUE constraint on NonIata code")
    void shouldEnforceUniqueNonIataCode() {
        NonIata agent1 = NonIata.builder()
                .code("AG-001")
                .name("Al-Amoudi Express")
                .status(NonIataStatus.ACTIVE)
                .build();
        nonIataRepository.saveAndFlush(agent1);

        NonIata agent2 = NonIata.builder()
                .code("AG-001")
                .name("Duplicate Code Agency")
                .status(NonIataStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> nonIataRepository.saveAndFlush(agent2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("Should enforce CHECK constraint on SalesTransaction amount (amount > 0)")
    void shouldEnforcePositiveAmountOnSalesTransaction() {
        User user = userRepository.saveAndFlush(User.builder()
                .username("sales_uploader")
                .passwordHash("hash")
                .fullName("Sales Uploader")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        NonIata nonIata = nonIataRepository.saveAndFlush(NonIata.builder()
                .code("AG-SALES")
                .name("Sales Agent")
                .status(NonIataStatus.ACTIVE)
                .build());

        ImportBatch batch = importBatchRepository.saveAndFlush(ImportBatch.builder()
                .fileName("galileo_test.xlsx")
                .objectKey("reports/2026/09/galileo_test.xlsx")
                .uploadedBy(user)
                .build());

        SalesTransaction zeroTx = SalesTransaction.builder()
                .nonIata(nonIata)
                .importBatch(batch)
                .saleDate(LocalDate.now())
                .amount(BigDecimal.ZERO)
                .sourceAgencyName("Raw Agency Name")
                .sourceRowNumber(1)
                .build();

        assertThatThrownBy(() -> salesTransactionRepository.saveAndFlush(zeroTx))
                .isInstanceOf(DataIntegrityViolationException.class);

        SalesTransaction negativeTx = SalesTransaction.builder()
                .nonIata(nonIata)
                .importBatch(batch)
                .saleDate(LocalDate.now())
                .amount(new BigDecimal("-150.00"))
                .sourceAgencyName("Raw Agency Name")
                .sourceRowNumber(2)
                .build();

        assertThatThrownBy(() -> salesTransactionRepository.saveAndFlush(negativeTx))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("Should enforce CHECK constraint on Payment amount (amount > 0)")
    void shouldEnforcePositiveAmountOnPayment() {
        User user = userRepository.saveAndFlush(User.builder()
                .username("pay_admin")
                .passwordHash("hash")
                .fullName("Payment Admin")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        NonIata nonIata = nonIataRepository.saveAndFlush(NonIata.builder()
                .code("AG-PAY")
                .name("Payment Agent")
                .status(NonIataStatus.ACTIVE)
                .build());

        Payment zeroPayment = Payment.builder()
                .nonIata(nonIata)
                .paymentDate(LocalDate.now())
                .amount(BigDecimal.ZERO)
                .createdBy(user)
                .build();

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(zeroPayment))
                .isInstanceOf(DataIntegrityViolationException.class);

        Payment negativePayment = Payment.builder()
                .nonIata(nonIata)
                .paymentDate(LocalDate.now())
                .amount(new BigDecimal("-500.00"))
                .createdBy(user)
                .build();

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(negativePayment))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("Should persist and link NameMapping to NonIata and creator User")
    void shouldPersistNameMapping() {
        User user = userRepository.saveAndFlush(User.builder()
                .username("mapper_user")
                .passwordHash("hash")
                .fullName("Mapper User")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        NonIata nonIata = nonIataRepository.saveAndFlush(NonIata.builder()
                .code("AG-MAP")
                .name("Mapped Agency")
                .status(NonIataStatus.ACTIVE)
                .build());

        NameMapping mapping = NameMapping.builder()
                .sourceName("GALILEO_AGENCY_XYZ")
                .nonIata(nonIata)
                .createdBy(user)
                .build();
        NameMapping saved = nameMappingRepository.saveAndFlush(mapping);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSourceName()).isEqualTo("GALILEO_AGENCY_XYZ");
        assertThat(saved.getNonIata().getCode()).isEqualTo("AG-MAP");
        assertThat(saved.getCreatedBy().getUsername()).isEqualTo("mapper_user");

        // Enforce uniqueness on source_name
        NameMapping duplicate = NameMapping.builder()
                .sourceName("GALILEO_AGENCY_XYZ")
                .nonIata(nonIata)
                .createdBy(user)
                .build();
        assertThatThrownBy(() -> nameMappingRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("Should persist AuditLog with or without authenticated user")
    void shouldPersistAuditLog() {
        User user = userRepository.saveAndFlush(User.builder()
                .username("audit_user")
                .passwordHash("hash")
                .fullName("Audit Actor")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        AuditLog userAudit = AuditLog.builder()
                .user(user)
                .action(AuditAction.LOGIN)
                .entityType("USER")
                .entityId(user.getId())
                .oldValue(null)
                .newValue("SUCCESS")
                .ipAddress("192.168.1.100")
                .build();
        AuditLog savedUserAudit = auditLogRepository.saveAndFlush(userAudit);
        assertThat(savedUserAudit.getId()).isNotNull();
        assertThat(savedUserAudit.getUser()).isNotNull();

        // System/anonymous event without user
        AuditLog systemAudit = AuditLog.builder()
                .user(null)
                .action(AuditAction.FILE_UPLOAD)
                .entityType("IMPORT_BATCH")
                .entityId(99L)
                .oldValue(null)
                .newValue("UPLOADED")
                .ipAddress("127.0.0.1")
                .build();
        AuditLog savedSystemAudit = auditLogRepository.saveAndFlush(systemAudit);
        assertThat(savedSystemAudit.getId()).isNotNull();
        assertThat(savedSystemAudit.getUser()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("Should enforce ON DELETE RESTRICT preventing deletion of NonIata with financial history")
    void shouldPreventCascadeDeletionOfNonIataWithFinancialHistory() {
        User user = userRepository.saveAndFlush(User.builder()
                .username("protect_user")
                .passwordHash("hash")
                .fullName("Protect User")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        NonIata nonIata = nonIataRepository.saveAndFlush(NonIata.builder()
                .code("AG-PROTECT")
                .name("Protected Agent")
                .status(NonIataStatus.ACTIVE)
                .build());

        Payment payment = Payment.builder()
                .nonIata(nonIata)
                .paymentDate(LocalDate.now())
                .amount(new BigDecimal("2500.00"))
                .createdBy(user)
                .build();
        paymentRepository.saveAndFlush(payment);

        // Attempting to delete the Non-IATA at the SQL level must trigger the database FK ON DELETE RESTRICT constraint
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM non_iatas WHERE id = ?", nonIata.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
