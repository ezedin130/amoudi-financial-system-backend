package com.amoudi.financial.service;

import com.amoudi.financial.dto.CreatePaymentRequest;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.dto.PaymentFilterRequest;
import com.amoudi.financial.dto.PaymentResponse;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * Service contract for recording, managing, cancelling payments and attaching receipts.
 */
public interface PaymentService {

    /**
     * Records a new payment transaction for a Non-IATA account.
     * Accessible by GENERAL_MANAGER only.
     */
    PaymentResponse createPayment(CreatePaymentRequest request, String username);

    /**
     * Attaches a receipt file (PDF, JPG, PNG) to an existing payment.
     * Accessible by GENERAL_MANAGER only.
     */
    PaymentResponse attachReceipt(Long paymentId, MultipartFile file, String username);

    /**
     * Retrieves the receipt file as a Spring Resource for download or viewing.
     * Accessible by FINANCE and GENERAL_MANAGER.
     */
    Resource getReceiptAsResource(Long paymentId);

    /**
     * Retrieves the MIME media type for the attached receipt.
     */
    String getReceiptContentType(Long paymentId);

    /**
     * Retrieves a single payment record by ID.
     */
    PaymentResponse getPaymentById(Long id);

    /**
     * Lists payments with dynamic filtering, pagination, and safe sorting.
     */
    PageResponse<PaymentResponse> listPayments(PaymentFilterRequest filter, Pageable pageable);

    /**
     * Cancels an existing POSTED payment transaction.
     * Accessible by FINANCE only.
     */
    PaymentResponse cancelPayment(Long paymentId, String username);

    /**
     * Calculates the sum of all POSTED payments for a Non-IATA account in ETB.
     */
    BigDecimal calculateTotalPostedPaymentsForNonIata(Long nonIataId);

    /**
     * Records an audit log entry for downloading or viewing a payment receipt.
     */
    void recordReceiptDownloadAudit(Long paymentId, String username);
}
