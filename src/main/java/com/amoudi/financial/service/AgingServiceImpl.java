package com.amoudi.financial.service;

import com.amoudi.financial.dto.AgingReportResponse;
import com.amoudi.financial.dto.AgingRowResponse;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.model.NonIata;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of AgingService calculating Accounts Receivable aging using FIFO allocation.
 */
@Service
@Transactional(readOnly = true)
public class AgingServiceImpl implements AgingService {

    private final NonIataRepository nonIataRepository;
    private final SalesTransactionRepository salesTransactionRepository;
    private final PaymentRepository paymentRepository;

    public AgingServiceImpl(
            NonIataRepository nonIataRepository,
            SalesTransactionRepository salesTransactionRepository,
            PaymentRepository paymentRepository
    ) {
        this.nonIataRepository = nonIataRepository;
        this.salesTransactionRepository = salesTransactionRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public AgingReportResponse getAgingReport(LocalDate asOfDate, Long nonIataId, String search, NonIataStatus status) {
        LocalDate effectiveAsOfDate = (asOfDate != null) ? asOfDate : LocalDate.now();

        // Build dynamic specification to filter Non-IATAs
        Specification<NonIata> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (nonIataId != null) {
                predicates.add(cb.equal(root.get("id"), nonIataId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<NonIata> nonIatas = nonIataRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "name"));

        List<AgingRowResponse> rows = new ArrayList<>(nonIatas.size());

        BigDecimal totalCurrent = BigDecimal.ZERO;
        BigDecimal totalDays31To60 = BigDecimal.ZERO;
        BigDecimal totalDays61To90 = BigDecimal.ZERO;
        BigDecimal totalDays91To120 = BigDecimal.ZERO;
        BigDecimal totalDays120Plus = BigDecimal.ZERO;
        BigDecimal grandTotalOutstanding = BigDecimal.ZERO;
        BigDecimal grandTotalSales = BigDecimal.ZERO;
        BigDecimal grandTotalPayments = BigDecimal.ZERO;

        for (NonIata nonIata : nonIatas) {
            AgingRowResponse row = calculateAgingForNonIata(nonIata, effectiveAsOfDate);
            rows.add(row);

            totalCurrent = totalCurrent.add(row.getCurrent());
            totalDays31To60 = totalDays31To60.add(row.getDays31To60());
            totalDays61To90 = totalDays61To90.add(row.getDays61To90());
            totalDays91To120 = totalDays91To120.add(row.getDays91To120());
            totalDays120Plus = totalDays120Plus.add(row.getDays120Plus());
            grandTotalOutstanding = grandTotalOutstanding.add(row.getTotalOutstanding());
            if (row.getTotalSales() != null) {
                grandTotalSales = grandTotalSales.add(row.getTotalSales());
            }
            if (row.getTotalPayments() != null) {
                grandTotalPayments = grandTotalPayments.add(row.getTotalPayments());
            }
        }

        return AgingReportResponse.builder()
                .asOfDate(effectiveAsOfDate)
                .totalCurrent(totalCurrent.setScale(2, RoundingMode.HALF_UP))
                .totalDays31To60(totalDays31To60.setScale(2, RoundingMode.HALF_UP))
                .totalDays61To90(totalDays61To90.setScale(2, RoundingMode.HALF_UP))
                .totalDays91To120(totalDays91To120.setScale(2, RoundingMode.HALF_UP))
                .totalDays120Plus(totalDays120Plus.setScale(2, RoundingMode.HALF_UP))
                .totalOutstanding(grandTotalOutstanding.setScale(2, RoundingMode.HALF_UP))
                .totalSales(grandTotalSales.setScale(2, RoundingMode.HALF_UP))
                .totalPayments(grandTotalPayments.setScale(2, RoundingMode.HALF_UP))
                .currency(Constants.DEFAULT_CURRENCY)
                .totalAccounts(rows.size())
                .rows(rows)
                .build();
    }

    private AgingRowResponse calculateAgingForNonIata(NonIata nonIata, LocalDate asOfDate) {
        String currency = Constants.DEFAULT_CURRENCY;

        // 1. Fetch all sales up to asOfDate in chronological order
        List<SalesTransaction> sales = salesTransactionRepository.findSalesForAging(nonIata.getId(), asOfDate);

        // 2. Fetch total posted payments up to asOfDate
        BigDecimal totalPayments = paymentRepository.sumPostedPaymentsUpToDate(nonIata.getId(), asOfDate, currency);

        // 3. Compute total sales (net of refunds) and total outstanding using authoritative grandTotal
        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal totalRefunds = BigDecimal.ZERO;
        List<SalesTransaction> positiveSales = new ArrayList<>();

        for (SalesTransaction s : sales) {
            BigDecimal amt = s.getGrandTotal() != null ? s.getGrandTotal() : s.getAmount();
            if (amt.compareTo(BigDecimal.ZERO) < 0) {
                totalRefunds = totalRefunds.add(amt.abs());
            } else {
                grossSales = grossSales.add(amt);
                positiveSales.add(s);
            }
        }

        BigDecimal netSales = grossSales.subtract(totalRefunds);
        BigDecimal totalOutstanding = netSales.subtract(totalPayments);

        // 4. FIFO application: apply total posted payments and refund credits against the oldest sales first
        BigDecimal remainingCredits = totalPayments.add(totalRefunds);

        BigDecimal current = BigDecimal.ZERO;
        BigDecimal days31To60 = BigDecimal.ZERO;
        BigDecimal days61To90 = BigDecimal.ZERO;
        BigDecimal days91To120 = BigDecimal.ZERO;
        BigDecimal days120Plus = BigDecimal.ZERO;

        for (SalesTransaction sale : positiveSales) {
            BigDecimal saleAmount = sale.getGrandTotal() != null ? sale.getGrandTotal() : sale.getAmount();

            if (remainingCredits.compareTo(saleAmount) >= 0) {
                // Sale is completely covered by payments/credits
                remainingCredits = remainingCredits.subtract(saleAmount);
            } else {
                // Sale is partially or fully uncovered
                BigDecimal unsettledAmount = saleAmount.subtract(remainingCredits);
                remainingCredits = BigDecimal.ZERO;

                long days = ChronoUnit.DAYS.between(sale.getSaleDate(), asOfDate);
                if (days <= 30) {
                    current = current.add(unsettledAmount);
                } else if (days <= 60) {
                    days31To60 = days31To60.add(unsettledAmount);
                } else if (days <= 90) {
                    days61To90 = days61To90.add(unsettledAmount);
                } else if (days <= 120) {
                    days91To120 = days91To120.add(unsettledAmount);
                } else {
                    days120Plus = days120Plus.add(unsettledAmount);
                }
            }
        }

        // If unallocated surplus credits remain, net against current bucket to maintain exact reconciliation
        if (remainingCredits.compareTo(BigDecimal.ZERO) > 0) {
            current = current.subtract(remainingCredits);
        }

        return AgingRowResponse.builder()
                .nonIataId(nonIata.getId())
                .code(nonIata.getCode())
                .name(nonIata.getName())
                .status(nonIata.getStatus())
                .current(current.setScale(2, RoundingMode.HALF_UP))
                .days31To60(days31To60.setScale(2, RoundingMode.HALF_UP))
                .days61To90(days61To90.setScale(2, RoundingMode.HALF_UP))
                .days91To120(days91To120.setScale(2, RoundingMode.HALF_UP))
                .days120Plus(days120Plus.setScale(2, RoundingMode.HALF_UP))
                .totalSales(netSales.setScale(2, RoundingMode.HALF_UP))
                .totalPayments(totalPayments.setScale(2, RoundingMode.HALF_UP))
                .totalOutstanding(totalOutstanding.setScale(2, RoundingMode.HALF_UP))
                .currency(currency)
                .build();
    }
}
