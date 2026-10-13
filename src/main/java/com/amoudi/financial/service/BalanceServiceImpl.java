package com.amoudi.financial.service;

import com.amoudi.financial.dto.CompanyFinancialOverviewResponse;
import com.amoudi.financial.dto.FinancialSummaryResponse;
import com.amoudi.financial.dto.NonIataFinancialProjection;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.exception.BadRequestException;
import com.amoudi.financial.exception.ResourceNotFoundException;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.PaymentRepository;
import com.amoudi.financial.repository.SalesTransactionRepository;
import com.amoudi.financial.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service implementation for dynamic financial balance calculation.
 *
 * Implements the financial formula:
 * OUTSTANDING BALANCE = TOTAL SALES - TOTAL POSTED PAYMENTS
 *
 * In Part 5, total payments is 0.00 as the Payment module has not yet been introduced.
 * Designed so that Part 7 can inject PaymentService.calculateTotalPostedPayments(...)
 * with zero redesign to the domain model or API contracts.
 */
@Service
@Transactional(readOnly = true)
public class BalanceServiceImpl implements BalanceService {

    private static final Logger log = LoggerFactory.getLogger(BalanceServiceImpl.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id",
            "code",
            "name",
            "status",
            "totalSales",
            "totalPayments",
            "outstandingBalance"
    );

    private final NonIataRepository nonIataRepository;
    private final SalesService salesService;
    private final PaymentService paymentService;
    private final SalesTransactionRepository salesTransactionRepository;
    private final PaymentRepository paymentRepository;

    public BalanceServiceImpl(
            NonIataRepository nonIataRepository,
            SalesService salesService,
            PaymentService paymentService,
            SalesTransactionRepository salesTransactionRepository,
            PaymentRepository paymentRepository
    ) {
        this.nonIataRepository = nonIataRepository;
        this.salesService = salesService;
        this.paymentService = paymentService;
        this.salesTransactionRepository = salesTransactionRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public FinancialSummaryResponse getFinancialSummary(Long nonIataId) {
        NonIata nonIata = nonIataRepository.findById(nonIataId)
                .orElseThrow(() -> new ResourceNotFoundException("NonIata", nonIataId));

        BigDecimal totalSales = salesService.calculateTotalSalesForNonIata(nonIataId);
        BigDecimal totalPayments = paymentService.calculateTotalPostedPaymentsForNonIata(nonIataId);

        log.debug("Calculated balance for Non-IATA '{}' (ID: {}): sales={}, payments={}, balance={}",
                nonIata.getCode(), nonIataId, totalSales, totalPayments, totalSales.subtract(totalPayments));

        return FinancialSummaryResponse.of(
                nonIata.getId(),
                nonIata.getCode(),
                nonIata.getName(),
                nonIata.getStatus(),
                totalSales,
                totalPayments,
                Constants.DEFAULT_CURRENCY
        );
    }

    @Override
    public PageResponse<FinancialSummaryResponse> listFinancialSummaries(
            String search,
            NonIataStatus status,
            Pageable pageable
    ) {
        Pageable safePageable = sanitizePageable(pageable);
        String searchPattern = (search != null && !search.trim().isEmpty())
                ? "%" + search.trim().toLowerCase() + "%"
                : null;

        Page<NonIataFinancialProjection> projections = nonIataRepository.findFinancialSummaries(
                searchPattern,
                status,
                safePageable
        );

        return PageResponse.of(projections, p -> FinancialSummaryResponse.of(
                p.getNonIataId(),
                p.getCode(),
                p.getName(),
                p.getStatus(),
                p.getTotalSales() != null ? p.getTotalSales() : BigDecimal.ZERO,
                p.getTotalPayments() != null ? p.getTotalPayments() : BigDecimal.ZERO,
                Constants.DEFAULT_CURRENCY
        ));
    }

    /**
     * Delegates to PaymentService to aggregate all POSTED payments in ETB.
     */
    protected BigDecimal calculateTotalPaymentsForNonIata(Long nonIataId) {
        return paymentService.calculateTotalPostedPaymentsForNonIata(nonIataId);
    }

    @Override
    public CompanyFinancialOverviewResponse getCompanyFinancialOverview() {
        BigDecimal totalSales = salesTransactionRepository.sumTotalSalesByCurrency(Constants.DEFAULT_CURRENCY);
        if (totalSales == null) {
            totalSales = BigDecimal.ZERO;
        }
        BigDecimal totalPayments = paymentRepository.sumTotalPostedPaymentsByCurrency(Constants.DEFAULT_CURRENCY);
        if (totalPayments == null) {
            totalPayments = BigDecimal.ZERO;
        }
        BigDecimal outstandingBalance = totalSales.subtract(totalPayments);
        long activeCount = nonIataRepository.countByStatus(NonIataStatus.ACTIVE);
        long totalCount = nonIataRepository.count();

        log.debug("Calculated company overview: totalSales={}, totalPayments={}, outstandingBalance={}, activeAccounts={}, totalAccounts={}",
                totalSales, totalPayments, outstandingBalance, activeCount, totalCount);

        return CompanyFinancialOverviewResponse.builder()
                .totalSales(totalSales.setScale(2, RoundingMode.HALF_UP))
                .totalPayments(totalPayments.setScale(2, RoundingMode.HALF_UP))
                .outstandingBalance(outstandingBalance.setScale(2, RoundingMode.HALF_UP))
                .activeAccountsCount(activeCount)
                .totalAccountsCount(totalCount)
                .currency(Constants.DEFAULT_CURRENCY)
                .build();
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int pageNumber = Math.max(0, pageable.getPageNumber());
        int pageSize = pageable.getPageSize() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.ASC, "name");
        } else {
            List<Sort.Order> safeOrders = new ArrayList<>();
            for (Sort.Order order : sort) {
                if (ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                    // Map outstandingBalance sort to totalSales since in Part 5 balance == sales
                    if ("outstandingBalance".equals(order.getProperty())) {
                        safeOrders.add(new Sort.Order(order.getDirection(), "totalSales"));
                    } else {
                        safeOrders.add(order);
                    }
                } else {
                    log.warn("Rejected invalid financial summary sort property: {}", order.getProperty());
                    throw new BadRequestException("Invalid sort property: '" + order.getProperty()
                            + "'. Allowed properties: " + ALLOWED_SORT_PROPERTIES);
                }
            }
            sort = Sort.by(safeOrders);
        }

        return PageRequest.of(pageNumber, pageSize, sort);
    }
}
