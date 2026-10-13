package com.amoudi.financial.service;

import com.amoudi.financial.dto.CreatePaymentRequest;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.dto.PaymentFilterRequest;
import com.amoudi.financial.dto.PaymentResponse;
import com.amoudi.financial.enums.AuditAction;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.PaymentStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.exception.BadRequestException;
import com.amoudi.financial.exception.FileValidationException;
import com.amoudi.financial.exception.ResourceNotFoundException;
import com.amoudi.financial.model.AuditLog;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.Payment;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.AuditLogRepository;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.PaymentRepository;
import com.amoudi.financial.repository.UserRepository;
import com.amoudi.financial.service.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private NonIataRepository nonIataRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private NonIata activeNonIata;
    private NonIata inactiveNonIata;
    private User gmUser;
    private Payment samplePayment;

    @BeforeEach
    void setUp() {
        activeNonIata = NonIata.builder()
                .code("HAL01")
                .name("Halifa Travel")
                .status(NonIataStatus.ACTIVE)
                .build();
        activeNonIata.setId(10L);

        inactiveNonIata = NonIata.builder()
                .code("INACT01")
                .name("Inactive Agency")
                .status(NonIataStatus.INACTIVE)
                .build();
        inactiveNonIata.setId(20L);

        gmUser = User.builder()
                .username("gm_admin")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build();
        gmUser.setId(1L);

        samplePayment = Payment.builder()
                .nonIata(activeNonIata)
                .paymentDate(LocalDate.now().minusDays(1))
                .amount(new BigDecimal("150000.00"))
                .currency("ETB")
                .receiptNumber("REC-001")
                .paymentReference("BANK-DEP-12345")
                .status(PaymentStatus.POSTED)
                .createdBy(gmUser)
                .build();
        samplePayment.setId(100L);
        samplePayment.setCreatedAt(LocalDateTime.now());
        samplePayment.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("createPayment: successfully creates payment with POSTED status and audit log")
    void createPayment_success() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .nonIataId(10L)
                .paymentDate(LocalDate.now())
                .amount(new BigDecimal("150000.00"))
                .currency("ETB")
                .receiptNumber("REC-001")
                .paymentReference("BANK-DEP-12345")
                .notes("Settlement for Sep 17 sales")
                .build();

        when(nonIataRepository.findById(10L)).thenReturn(Optional.of(activeNonIata));
        when(userRepository.findByUsername("gm_admin")).thenReturn(Optional.of(gmUser));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(101L);
            return p;
        });

        PaymentResponse response = paymentService.createPayment(request, "gm_admin");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getNonIataId()).isEqualTo(10L);
        assertThat(response.getNonIataCode()).isEqualTo("HAL01");
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(response.getCurrency()).isEqualTo("ETB");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.POSTED);
        assertThat(response.getReceiptNumber()).isEqualTo("REC-001");
        assertThat(response.getPaymentReference()).isEqualTo("BANK-DEP-12345");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertThat(audit.getAction()).isEqualTo(AuditAction.PAYMENT);
        assertThat(audit.getEntityType()).isEqualTo("Payment");
        assertThat(audit.getEntityId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("createPayment: throws ResourceNotFoundException when Non-IATA does not exist")
    void createPayment_nonExistentNonIata_throwsException() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .nonIataId(999L)
                .paymentDate(LocalDate.now())
                .amount(new BigDecimal("100000.00"))
                .build();

        when(nonIataRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(request, "gm_admin"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPayment: throws BadRequestException when Non-IATA is INACTIVE")
    void createPayment_inactiveNonIata_throwsBadRequest() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .nonIataId(20L)
                .paymentDate(LocalDate.now())
                .amount(new BigDecimal("100000.00"))
                .build();

        when(nonIataRepository.findById(20L)).thenReturn(Optional.of(inactiveNonIata));

        assertThatThrownBy(() -> paymentService.createPayment(request, "gm_admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("inactive Non-IATA");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPayment: throws BadRequestException when amount is zero or negative")
    void createPayment_invalidAmount_throwsBadRequest() {
        CreatePaymentRequest zeroRequest = CreatePaymentRequest.builder()
                .nonIataId(10L)
                .paymentDate(LocalDate.now())
                .amount(BigDecimal.ZERO)
                .build();

        when(nonIataRepository.findById(10L)).thenReturn(Optional.of(activeNonIata));

        assertThatThrownBy(() -> paymentService.createPayment(zeroRequest, "gm_admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    @DisplayName("createPayment: throws BadRequestException when payment date is in the future")
    void createPayment_futureDate_throwsBadRequest() {
        CreatePaymentRequest futureRequest = CreatePaymentRequest.builder()
                .nonIataId(10L)
                .paymentDate(LocalDate.now().plusDays(2))
                .amount(new BigDecimal("50000.00"))
                .build();

        when(nonIataRepository.findById(10L)).thenReturn(Optional.of(activeNonIata));

        assertThatThrownBy(() -> paymentService.createPayment(futureRequest, "gm_admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("future");
    }

    @Test
    @DisplayName("createPayment: throws BadRequestException when currency is not ETB")
    void createPayment_unsupportedCurrency_throwsBadRequest() {
        CreatePaymentRequest usdRequest = CreatePaymentRequest.builder()
                .nonIataId(10L)
                .paymentDate(LocalDate.now())
                .amount(new BigDecimal("50000.00"))
                .currency("USD")
                .build();

        when(nonIataRepository.findById(10L)).thenReturn(Optional.of(activeNonIata));

        assertThatThrownBy(() -> paymentService.createPayment(usdRequest, "gm_admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("USD");
    }

    @Test
    @DisplayName("cancelPayment: successfully transitions POSTED payment to CANCELLED")
    void cancelPayment_success() {
        when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(samplePayment));
        when(userRepository.findByUsername("finance_user")).thenReturn(Optional.of(gmUser));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.cancelPayment(100L, "finance_user");

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertThat(audit.getAction()).isEqualTo(AuditAction.PAYMENT_CANCEL);
        assertThat(audit.getEntityId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("cancelPayment: throws BadRequestException when payment is already CANCELLED")
    void cancelPayment_alreadyCancelled_throwsBadRequest() {
        samplePayment.setStatus(PaymentStatus.CANCELLED);
        when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(samplePayment));

        assertThatThrownBy(() -> paymentService.cancelPayment(100L, "finance_user"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already CANCELLED");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("attachReceipt: successfully stores file and updates receiptObjectKey")
    void attachReceipt_success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bank_slip.pdf",
                "application/pdf",
                "%PDF-1.4 test receipt".getBytes()
        );

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(samplePayment));
        when(userRepository.findByUsername("gm_admin")).thenReturn(Optional.of(gmUser));
        when(fileStorageService.store(file, "receipts")).thenReturn("receipts/20260930/test-uuid_bank_slip.pdf");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.attachReceipt(100L, file, "gm_admin");

        assertThat(response.isHasReceipt()).isTrue();
        assertThat(response.getReceiptObjectKey()).isEqualTo("receipts/20260930/test-uuid_bank_slip.pdf");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo(AuditAction.FILE_UPLOAD);
    }

    @Test
    @DisplayName("attachReceipt: throws FileValidationException on unsupported file format")
    void attachReceipt_unsupportedFormat_throwsFileValidationException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious_script.exe",
                "application/octet-stream",
                "evil payload".getBytes()
        );

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(samplePayment));

        assertThatThrownBy(() -> paymentService.attachReceipt(100L, file, "gm_admin"))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("Allowed formats: PDF, JPG, JPEG, PNG");
    }

    @Test
    @DisplayName("attachReceipt: throws FileValidationException on empty file")
    void attachReceipt_emptyFile_throwsFileValidationException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                new byte[0]
        );

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(samplePayment));

        assertThatThrownBy(() -> paymentService.attachReceipt(100L, emptyFile, "gm_admin"))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("getReceiptAsResource: retrieves file stream for payment with attached receipt")
    void getReceiptAsResource_success() throws IOException {
        samplePayment.setReceiptObjectKey("receipts/20260930/test-uuid_receipt.pdf");
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(samplePayment));
        when(fileStorageService.retrieve("receipts/20260930/test-uuid_receipt.pdf"))
                .thenReturn(new ByteArrayInputStream("%PDF-1.4 test".getBytes()));

        Resource resource = paymentService.getReceiptAsResource(100L);

        assertThat(resource).isNotNull();
        assertThat(resource.getInputStream().available()).isGreaterThan(0);
    }

    @Test
    @DisplayName("getReceiptAsResource: throws ResourceNotFoundException when payment has no attached receipt")
    void getReceiptAsResource_noReceipt_throwsResourceNotFound() {
        samplePayment.setReceiptObjectKey(null);
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(samplePayment));

        assertThatThrownBy(() -> paymentService.getReceiptAsResource(100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("paymentId: 100");
    }

    @Test
    @DisplayName("calculateTotalPostedPaymentsForNonIata: aggregates sum of posted payments in ETB")
    void calculateTotalPostedPaymentsForNonIata_success() {
        when(nonIataRepository.existsById(10L)).thenReturn(true);
        when(paymentRepository.findDistinctCurrenciesByNonIataIdAndStatus(10L, PaymentStatus.POSTED))
                .thenReturn(List.of("ETB"));
        when(paymentRepository.sumPostedPaymentsByNonIataIdAndCurrency(10L, "ETB"))
                .thenReturn(new BigDecimal("250000.00"));

        BigDecimal total = paymentService.calculateTotalPostedPaymentsForNonIata(10L);

        assertThat(total).isEqualByComparingTo(new BigDecimal("250000.00"));
    }

    @Test
    @DisplayName("calculateTotalPostedPaymentsForNonIata: returns 0.00 when no payments exist")
    void calculateTotalPostedPaymentsForNonIata_noPayments_returnsZero() {
        when(nonIataRepository.existsById(10L)).thenReturn(true);
        when(paymentRepository.findDistinctCurrenciesByNonIataIdAndStatus(10L, PaymentStatus.POSTED))
                .thenReturn(List.of());
        when(paymentRepository.sumPostedPaymentsByNonIataIdAndCurrency(10L, "ETB"))
                .thenReturn(null);

        BigDecimal total = paymentService.calculateTotalPostedPaymentsForNonIata(10L);

        assertThat(total).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("calculateTotalPostedPaymentsForNonIata: throws BadRequestException on multi-currency posted payments")
    void calculateTotalPostedPaymentsForNonIata_multiCurrency_throwsBadRequest() {
        when(nonIataRepository.existsById(10L)).thenReturn(true);
        when(paymentRepository.findDistinctCurrenciesByNonIataIdAndStatus(10L, PaymentStatus.POSTED))
                .thenReturn(List.of("ETB", "USD"));

        assertThatThrownBy(() -> paymentService.calculateTotalPostedPaymentsForNonIata(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("multiple currencies");
    }

    @Test
    @DisplayName("listPayments: returns paginated PaymentResponse results")
    void listPayments_success() {
        Page<Payment> page = new PageImpl<>(List.of(samplePayment), PageRequest.of(0, 10), 1);
        when(paymentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PaymentFilterRequest filter = PaymentFilterRequest.builder().nonIataId(10L).build();
        PageResponse<PaymentResponse> result = paymentService.listPayments(filter, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(100L);
        assertThat(result.getContent().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("150000.00"));
    }
}
