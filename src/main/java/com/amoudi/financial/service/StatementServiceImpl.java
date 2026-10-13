package com.amoudi.financial.service;

import com.amoudi.financial.dto.AccountStatementResponse;
import com.amoudi.financial.dto.StatementTransactionResponse;
import com.amoudi.financial.enums.PaymentStatus;
import com.amoudi.financial.enums.StatementTransactionType;
import com.amoudi.financial.exception.BadRequestException;
import com.amoudi.financial.exception.ResourceNotFoundException;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.Payment;
import com.amoudi.financial.model.SalesTransaction;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.PaymentRepository;
import com.amoudi.financial.repository.SalesTransactionRepository;
import com.amoudi.financial.utils.Constants;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Implementation of StatementService for generating deterministic account statements.
 */
@Service
@Transactional(readOnly = true)
public class StatementServiceImpl implements StatementService {

    private final NonIataRepository nonIataRepository;
    private final SalesTransactionRepository salesTransactionRepository;
    private final PaymentRepository paymentRepository;

    public StatementServiceImpl(
            NonIataRepository nonIataRepository,
            SalesTransactionRepository salesTransactionRepository,
            PaymentRepository paymentRepository
    ) {
        this.nonIataRepository = nonIataRepository;
        this.salesTransactionRepository = salesTransactionRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public AccountStatementResponse getStatement(Long nonIataId, LocalDate dateFrom, LocalDate dateTo) {
        NonIata nonIata = nonIataRepository.findById(nonIataId)
                .orElseThrow(() -> new ResourceNotFoundException("Non-IATA not found with ID: " + nonIataId));

        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BadRequestException("Start date (dateFrom) cannot be after end date (dateTo)");
        }

        String currency = Constants.DEFAULT_CURRENCY;

        // 1. Calculate opening balance (sales before dateFrom minus posted payments before dateFrom)
        BigDecimal openingBalance;
        if (dateFrom != null) {
            BigDecimal salesBefore = salesTransactionRepository.sumSalesBeforeDate(nonIataId, dateFrom, currency);
            BigDecimal paymentsBefore = paymentRepository.sumPostedPaymentsBeforeDate(nonIataId, dateFrom, currency);
            openingBalance = salesBefore.subtract(paymentsBefore).setScale(2, RoundingMode.HALF_UP);
        } else {
            openingBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // 2. Fetch sales and posted payments within the period using dynamic Specifications
        Specification<SalesTransaction> salesSpec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("nonIata").get("id"), nonIataId));
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("saleDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("saleDate"), dateTo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<SalesTransaction> sales = salesTransactionRepository.findAll(
                salesSpec,
                Sort.by(Sort.Direction.ASC, "saleDate").and(Sort.by(Sort.Direction.ASC, "id"))
        );

        Specification<Payment> paymentSpec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("nonIata").get("id"), nonIataId));
            predicates.add(cb.equal(root.get("status"), PaymentStatus.POSTED));
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("paymentDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("paymentDate"), dateTo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<Payment> payments = paymentRepository.findAll(
                paymentSpec,
                Sort.by(Sort.Direction.ASC, "paymentDate").and(Sort.by(Sort.Direction.ASC, "id"))
        );

        // 3. Transform to unified statement transactions
        List<StatementTransactionResponse> allTransactions = new ArrayList<>(sales.size() + payments.size());

        for (SalesTransaction sale : sales) {
            String passenger = sale.getPassengerName() != null ? sale.getPassengerName() : "N/A";
            String route = sale.getRoute() != null ? " / " + sale.getRoute() : "";
            BigDecimal saleDebit = (sale.getGrandTotal() != null ? sale.getGrandTotal() : sale.getAmount())
                    .setScale(2, RoundingMode.HALF_UP);
            allTransactions.add(StatementTransactionResponse.builder()
                    .transactionDate(sale.getSaleDate())
                    .type(StatementTransactionType.SALE)
                    .reference(sale.getTicketNumber())
                    .description("Ticket Sale: " + passenger + route)
                    .debit(saleDebit)
                    .credit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .baseFare(sale.getBaseFare())
                    .tax(sale.getTax())
                    .totalSale(sale.getTotalSale())
                    .serviceCharge(sale.getServiceCharge())
                    .grandTotal(saleDebit)
                    .sourceTransactionId(sale.getId())
                    .currency(sale.getCurrency())
                    .build());
        }

        for (Payment payment : payments) {
            String ref;
            if (payment.getReceiptNumber() != null && !payment.getReceiptNumber().isBlank()) {
                ref = payment.getReceiptNumber();
            } else if (payment.getPaymentReference() != null && !payment.getPaymentReference().isBlank()) {
                ref = payment.getPaymentReference();
            } else {
                ref = "PAY-" + payment.getId();
            }

            StringBuilder desc = new StringBuilder("Payment");
            if (payment.getPaymentReference() != null && !payment.getPaymentReference().isBlank()) {
                desc.append(" (Ref: ").append(payment.getPaymentReference()).append(")");
            }
            if (payment.getNotes() != null && !payment.getNotes().isBlank()) {
                desc.append(" - ").append(payment.getNotes());
            }

            allTransactions.add(StatementTransactionResponse.builder()
                    .transactionDate(payment.getPaymentDate())
                    .type(StatementTransactionType.PAYMENT)
                    .reference(ref)
                    .description(desc.toString())
                    .debit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .credit(payment.getAmount().setScale(2, RoundingMode.HALF_UP))
                    .sourceTransactionId(payment.getId())
                    .currency(payment.getCurrency())
                    .build());
        }

        // 4. Sort deterministically: transactionDate ASC -> type (SALE before PAYMENT) -> sourceTransactionId ASC
        allTransactions.sort(Comparator
                .comparing(StatementTransactionResponse::getTransactionDate)
                .thenComparingInt(tx -> tx.getType() == StatementTransactionType.SALE ? 0 : 1)
                .thenComparing(StatementTransactionResponse::getSourceTransactionId));

        // 5. Compute running balances and period totals
        BigDecimal runningBalance = openingBalance;
        BigDecimal totalPeriodSales = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPeriodPayments = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (StatementTransactionResponse tx : allTransactions) {
            if (tx.getType() == StatementTransactionType.SALE) {
                runningBalance = runningBalance.add(tx.getDebit());
                totalPeriodSales = totalPeriodSales.add(tx.getDebit());
            } else {
                runningBalance = runningBalance.subtract(tx.getCredit());
                totalPeriodPayments = totalPeriodPayments.add(tx.getCredit());
            }
            tx.setRunningBalance(runningBalance.setScale(2, RoundingMode.HALF_UP));
        }

        BigDecimal closingBalance = runningBalance.setScale(2, RoundingMode.HALF_UP);

        return AccountStatementResponse.builder()
                .nonIataId(nonIata.getId())
                .nonIataCode(nonIata.getCode())
                .nonIataName(nonIata.getName())
                .currency(currency)
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .openingBalance(openingBalance)
                .totalSales(totalPeriodSales)
                .totalPayments(totalPeriodPayments)
                .closingBalance(closingBalance)
                .transactionCount(allTransactions.size())
                .transactions(allTransactions)
                .build();
    }
}
