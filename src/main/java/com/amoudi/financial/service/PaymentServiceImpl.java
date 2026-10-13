package com.amoudi.financial.service;

import com.amoudi.financial.dto.CreatePaymentRequest;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.dto.PaymentFilterRequest;
import com.amoudi.financial.dto.PaymentResponse;
import com.amoudi.financial.enums.AuditAction;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.PaymentStatus;
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
import com.amoudi.financial.repository.PaymentSpecification;
import com.amoudi.financial.repository.UserRepository;
import com.amoudi.financial.service.storage.FileStorageService;
import com.amoudi.financial.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service implementation for Non-IATA payment and receipt management.
 */
@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private static final long MAX_RECEIPT_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".jpg", ".jpeg", ".png");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id",
            "paymentDate",
            "amount",
            "createdAt",
            "receiptNumber",
            "paymentReference",
            "status"
    );

    private final PaymentRepository paymentRepository;
    private final NonIataRepository nonIataRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final FileStorageService fileStorageService;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            NonIataRepository nonIataRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            FileStorageService fileStorageService
    ) {
        this.paymentRepository = paymentRepository;
        this.nonIataRepository = nonIataRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public PaymentResponse createPayment(CreatePaymentRequest request, String username) {
        log.info("Creating payment for Non-IATA ID {} by user {}", request.getNonIataId(), username);

        NonIata nonIata = nonIataRepository.findById(request.getNonIataId())
                .orElseThrow(() -> new ResourceNotFoundException("NonIata", request.getNonIataId()));

        if (nonIata.getStatus() != NonIataStatus.ACTIVE) {
            log.warn("Attempted payment creation for inactive Non-IATA account: {}", nonIata.getCode());
            throw new BadRequestException("Cannot create payment for inactive Non-IATA account: " + nonIata.getCode());
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Payment amount must be greater than zero");
        }

        if (request.getPaymentDate() == null) {
            throw new BadRequestException("Payment date is required");
        }

        if (request.getPaymentDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("Payment date cannot be in the future: " + request.getPaymentDate());
        }

        String currency = StringUtils.hasText(request.getCurrency()) ? request.getCurrency().trim().toUpperCase() : Constants.DEFAULT_CURRENCY;
        if (!Constants.DEFAULT_CURRENCY.equalsIgnoreCase(currency)) {
            throw new BadRequestException("Unsupported currency: '" + currency + "'. Only " + Constants.DEFAULT_CURRENCY + " is supported in V1.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username: " + username));

        BigDecimal scaledAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

        // Validate overpayment policy
        validateOverpaymentPolicy(nonIata, scaledAmount);

        Payment payment = Payment.builder()
                .nonIata(nonIata)
                .paymentDate(request.getPaymentDate())
                .amount(scaledAmount)
                .currency(currency)
                .receiptNumber(StringUtils.hasText(request.getReceiptNumber()) ? request.getReceiptNumber().trim() : null)
                .paymentReference(StringUtils.hasText(request.getPaymentReference()) ? request.getPaymentReference().trim() : null)
                .notes(StringUtils.hasText(request.getNotes()) ? request.getNotes().trim() : null)
                .status(PaymentStatus.POSTED)
                .createdBy(user)
                .build();

        Payment saved = paymentRepository.save(payment);

        // Record Audit Log
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .action(AuditAction.PAYMENT)
                    .entityType("Payment")
                    .entityId(saved.getId())
                    .newValue(String.format("Created payment id=%d, nonIata=%s, amount=%s %s, date=%s, ref=%s",
                            saved.getId(), nonIata.getCode(), saved.getAmount(), saved.getCurrency(),
                            saved.getPaymentDate(), saved.getPaymentReference()))
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to record audit log for payment creation id {}: {}", saved.getId(), e.getMessage());
        }

        log.info("Payment id={} created successfully with status POSTED for Non-IATA {}", saved.getId(), nonIata.getCode());
        return PaymentResponse.of(saved);
    }

    @Override
    public PaymentResponse attachReceipt(Long paymentId, MultipartFile file, String username) {
        log.info("Attaching receipt to payment id={} by user={}", paymentId, username);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        validateReceiptFile(file);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username: " + username));

        String objectKey;
        try {
            objectKey = fileStorageService.store(file, "receipts");
        } catch (IOException e) {
            log.error("Failed to store receipt file for payment id={}: {}", paymentId, e.getMessage(), e);
            throw new BadRequestException("Failed to store receipt file: " + e.getMessage());
        }

        String oldObjectKey = payment.getReceiptObjectKey();
        payment.setReceiptObjectKey(objectKey);
        Payment updated = paymentRepository.save(payment);

        // Safely remove replaced receipt file to avoid orphaned files on storage
        if (StringUtils.hasText(oldObjectKey) && !oldObjectKey.equals(objectKey)) {
            try {
                fileStorageService.delete(oldObjectKey);
                log.info("Deleted superseded receipt file '{}' for payment id={}", oldObjectKey, paymentId);
            } catch (Exception ex) {
                log.warn("Failed to delete superseded receipt file '{}': {}", oldObjectKey, ex.getMessage());
            }
        }

        // Record Audit Log
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .action(AuditAction.FILE_UPLOAD)
                    .entityType("Payment")
                    .entityId(updated.getId())
                    .oldValue(oldObjectKey != null ? "receipt_object_key: " + oldObjectKey : null)
                    .newValue("Attached receipt_object_key: " + objectKey)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to record audit log for receipt upload on payment id {}: {}", updated.getId(), e.getMessage());
        }

        log.info("Receipt attached successfully to payment id={}: objectKey={}", updated.getId(), objectKey);
        return PaymentResponse.of(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource getReceiptAsResource(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if (!StringUtils.hasText(payment.getReceiptObjectKey())) {
            throw new ResourceNotFoundException("Receipt", "paymentId: " + paymentId);
        }

        try {
            InputStream is = fileStorageService.retrieve(payment.getReceiptObjectKey());
            return new InputStreamResource(is);
        } catch (IOException e) {
            log.error("Receipt file not found on storage for payment id {}: {}", paymentId, e.getMessage());
            throw new ResourceNotFoundException("Receipt file not found on storage: " + payment.getReceiptObjectKey());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String getReceiptContentType(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if (!StringUtils.hasText(payment.getReceiptObjectKey())) {
            throw new ResourceNotFoundException("Receipt", "paymentId: " + paymentId);
        }

        String lower = payment.getReceiptObjectKey().toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lower.endsWith(".png")) {
            return "image/png";
        }
        return "application/octet-stream";
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        return PaymentResponse.of(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> listPayments(PaymentFilterRequest filter, Pageable pageable) {
        Pageable safePageable = sanitizePageable(pageable);

        Specification<Payment> spec = (filter != null)
                ? PaymentSpecification.withFilters(
                        filter.getNonIataId(),
                        filter.getPaymentDate(),
                        filter.getDateFrom(),
                        filter.getDateTo(),
                        filter.getReceiptNumber(),
                        filter.getPaymentReference(),
                        filter.getStatus()
                )
                : null;

        Page<Payment> page = (spec != null)
                ? paymentRepository.findAll(spec, safePageable)
                : paymentRepository.findAll(safePageable);

        return PageResponse.of(page, PaymentResponse::of);
    }

    @Override
    public PaymentResponse cancelPayment(Long paymentId, String username) {
        log.info("Cancelling payment id={} by user={}", paymentId, username);

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            log.warn("Attempted cancellation of already CANCELLED payment id={}", paymentId);
            throw new BadRequestException("Payment with ID " + paymentId + " is already CANCELLED");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username: " + username));

        payment.setStatus(PaymentStatus.CANCELLED);
        Payment updated = paymentRepository.save(payment);

        // Record Audit Log
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .action(AuditAction.PAYMENT_CANCEL)
                    .entityType("Payment")
                    .entityId(updated.getId())
                    .oldValue("status: POSTED")
                    .newValue("status: CANCELLED")
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to record audit log for payment cancellation id {}: {}", updated.getId(), e.getMessage());
        }

        log.info("Payment id={} cancelled successfully by user={}", updated.getId(), username);
        return PaymentResponse.of(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalPostedPaymentsForNonIata(Long nonIataId) {
        if (!nonIataRepository.existsById(nonIataId)) {
            throw new ResourceNotFoundException("NonIata", nonIataId);
        }

        List<String> currencies = paymentRepository.findDistinctCurrenciesByNonIataIdAndStatus(nonIataId, PaymentStatus.POSTED);
        if (currencies.size() > 1) {
            log.error("Non-IATA ID {} has posted payments across multiple currencies: {}", nonIataId, currencies);
            throw new BadRequestException("Non-IATA account has posted payments across multiple currencies: "
                    + currencies + ". Cannot calculate a single unified balance.");
        }

        if (currencies.size() == 1 && !Constants.DEFAULT_CURRENCY.equalsIgnoreCase(currencies.get(0))) {
            log.error("Non-IATA ID {} has posted payments in unsupported currency: {}", nonIataId, currencies.get(0));
            throw new BadRequestException("Non-IATA account has posted payments in unsupported currency: "
                    + currencies.get(0) + ". Only " + Constants.DEFAULT_CURRENCY + " is supported in V1.");
        }

        BigDecimal total = paymentRepository.sumPostedPaymentsByNonIataIdAndCurrency(nonIataId, Constants.DEFAULT_CURRENCY);
        return (total != null ? total : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Isolated validation policy for payment amounts exceeding current sales.
     * In V1, sub-agents frequently submit deposits or advance prepayments prior to ticket issuance.
     * Overpayment (resulting in negative outstanding balance) is permitted as account credit.
     */
    protected void validateOverpaymentPolicy(NonIata nonIata, BigDecimal paymentAmount) {
        log.debug("Overpayment policy check evaluated for Non-IATA {}: amount={}", nonIata.getCode(), paymentAmount);
    }

    private void validateReceiptFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileValidationException("Receipt file cannot be empty");
        }

        if (file.getSize() > MAX_RECEIPT_FILE_SIZE) {
            throw new FileValidationException("Receipt file exceeds maximum allowed size of 10MB (file size: " + file.getSize() + " bytes)");
        }

        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new FileValidationException("Receipt file must have a valid filename");
        }

        String lowerName = originalFilename.toLowerCase();
        boolean validExtension = ALLOWED_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
        if (!validExtension) {
            throw new FileValidationException("Unsupported receipt file format: '" + originalFilename
                    + "'. Allowed formats: PDF, JPG, JPEG, PNG");
        }

        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            log.warn("MIME type mismatch for file {}: {}", originalFilename, contentType);
            // Some browsers report application/octet-stream or image/pjpeg for valid files;
            // if extension is valid and content type is not explicitly blocked, we allow it with a log warning.
        }
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int pageNumber = Math.max(0, pageable.getPageNumber());
        int pageSize = pageable.getPageSize() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "paymentDate").and(Sort.by(Sort.Direction.DESC, "id"));
        } else {
            List<Sort.Order> safeOrders = new ArrayList<>();
            for (Sort.Order order : sort) {
                if (ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                    safeOrders.add(order);
                } else {
                    log.warn("Rejected invalid payment sort property: {}", order.getProperty());
                    throw new BadRequestException("Invalid sort property: '" + order.getProperty()
                            + "'. Allowed properties: " + ALLOWED_SORT_PROPERTIES);
                }
            }
            sort = Sort.by(safeOrders);
        }

        return PageRequest.of(pageNumber, pageSize, sort);
    }

    @Override
    @Transactional
    public void recordReceiptDownloadAudit(Long paymentId, String username) {
        try {
            User user = userRepository.findByUsername(username).orElse(null);
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .action(AuditAction.FILE_DOWNLOAD)
                    .entityType("Payment")
                    .entityId(paymentId)
                    .newValue("Downloaded or viewed receipt for payment id=" + paymentId)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to record audit log for receipt download on payment id {}: {}", paymentId, e.getMessage());
        }
    }
}
