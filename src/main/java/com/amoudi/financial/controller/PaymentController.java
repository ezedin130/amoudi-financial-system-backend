package com.amoudi.financial.controller;

import com.amoudi.financial.dto.ApiResponse;
import com.amoudi.financial.dto.CreatePaymentRequest;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.dto.PaymentFilterRequest;
import com.amoudi.financial.dto.PaymentResponse;
import com.amoudi.financial.enums.PaymentStatus;
import com.amoudi.financial.service.PaymentService;
import com.amoudi.financial.utils.Constants;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * REST controller for Non-IATA payment recording, receipt attachment, viewing, and cancellation.
 */
@RestController
@RequestMapping(Constants.PAYMENTS_API_PREFIX)
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Records a new payment transaction for a Non-IATA account.
     * Restricted to GENERAL_MANAGER role.
     */
    @PostMapping
    @PreAuthorize("hasRole('GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            Authentication authentication
    ) {
        PaymentResponse response = paymentService.createPayment(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Payment created successfully", response));
    }

    /**
     * Uploads and attaches a receipt file (PDF, JPG, PNG) to an existing payment.
     * Restricted to GENERAL_MANAGER role.
     */
    @PostMapping(value = "/{id}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> attachReceipt(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        PaymentResponse response = paymentService.attachReceipt(id, file, authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Receipt attached successfully", response));
    }

    /**
     * Retrieves the binary receipt file associated with a payment for viewing or download.
     * Accessible by both FINANCE and GENERAL_MANAGER.
     */
    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<Resource> getReceipt(@PathVariable Long id, Authentication authentication) {
        Resource resource = paymentService.getReceiptAsResource(id);
        String contentType = paymentService.getReceiptContentType(id);
        paymentService.recordReceiptDownloadAudit(id, authentication != null ? authentication.getName() : "system");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"receipt-" + id + "\"")
                .body(resource);
    }

    /**
     * Retrieves paginated payment transactions with dynamic filtering and safe sorting.
     * Accessible by both FINANCE and GENERAL_MANAGER.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> listPayments(
            @RequestParam(required = false) Long nonIataId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String receiptNumber,
            @RequestParam(required = false) String paymentReference,
            @RequestParam(required = false) PaymentStatus status,
            @PageableDefault(size = 20, sort = "paymentDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PaymentFilterRequest filter = PaymentFilterRequest.builder()
                .nonIataId(nonIataId)
                .paymentDate(paymentDate)
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .receiptNumber(receiptNumber)
                .paymentReference(paymentReference)
                .status(status)
                .build();

        PageResponse<PaymentResponse> result = paymentService.listPayments(filter, pageable);
        return ResponseEntity.ok(ApiResponse.ok("Payments retrieved successfully", result));
    }

    /**
     * Retrieves a single payment record by ID.
     * Accessible by both FINANCE and GENERAL_MANAGER.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(@PathVariable Long id) {
        PaymentResponse response = paymentService.getPaymentById(id);
        return ResponseEntity.ok(ApiResponse.ok("Payment retrieved successfully", response));
    }

    /**
     * Cancels an existing POSTED payment transaction.
     * Accessible by FINANCE and GENERAL_MANAGER roles.
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('FINANCE', 'GENERAL_MANAGER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancelPayment(
            @PathVariable Long id,
            Authentication authentication
    ) {
        PaymentResponse response = paymentService.cancelPayment(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Payment cancelled successfully", response));
    }
}
