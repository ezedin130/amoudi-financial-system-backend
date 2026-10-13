package com.amoudi.financial.service;

import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.dto.SalesFilterRequest;
import com.amoudi.financial.dto.SalesTransactionResponse;
import com.amoudi.financial.exception.BadRequestException;
import com.amoudi.financial.exception.ResourceNotFoundException;
import com.amoudi.financial.model.SalesTransaction;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.SalesTransactionRepository;
import com.amoudi.financial.repository.SalesTransactionSpecification;
import com.amoudi.financial.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service implementation for querying sales transactions and performing sales aggregations.
 */
@Service
@Transactional(readOnly = true)
public class SalesServiceImpl implements SalesService {

    private static final Logger log = LoggerFactory.getLogger(SalesServiceImpl.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id",
            "saleDate",
            "amount",
            "ticketNumber",
            "transactionReference",
            "passengerName",
            "createdAt",
            "grandTotal",
            "baseFare",
            "tax",
            "totalSale",
            "serviceCharge"
    );

    private final SalesTransactionRepository salesTransactionRepository;
    private final NonIataRepository nonIataRepository;

    public SalesServiceImpl(
            SalesTransactionRepository salesTransactionRepository,
            NonIataRepository nonIataRepository
    ) {
        this.salesTransactionRepository = salesTransactionRepository;
        this.nonIataRepository = nonIataRepository;
    }

    @Override
    public PageResponse<SalesTransactionResponse> listSales(SalesFilterRequest filter, Pageable pageable) {
        if (filter != null && filter.getStartDate() != null && filter.getEndDate() != null) {
            if (filter.getStartDate().isAfter(filter.getEndDate())) {
                log.warn("Invalid date range in sales query: startDate {} is after endDate {}",
                        filter.getStartDate(), filter.getEndDate());
                throw new BadRequestException("Start date cannot be after end date");
            }
        }

        Pageable safePageable = sanitizePageable(pageable);

        Specification<SalesTransaction> spec = (filter == null)
                ? (root, query, cb) -> cb.conjunction()
                : SalesTransactionSpecification.withFilters(
                        filter.getNonIataId(),
                        filter.getSaleDate(),
                        filter.getStartDate(),
                        filter.getEndDate(),
                        filter.getTicketNumber(),
                        filter.getTransactionReference(),
                        filter.getImportBatchId()
                );

        Page<SalesTransaction> page = salesTransactionRepository.findAll(spec, safePageable);
        return PageResponse.of(page, SalesTransactionResponse::fromEntity);
    }

    @Override
    public SalesTransactionResponse getSaleById(Long id) {
        SalesTransaction sale = salesTransactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SalesTransaction", id));
        return SalesTransactionResponse.fromEntity(sale);
    }

    @Override
    public BigDecimal calculateTotalSalesForNonIata(Long nonIataId) {
        if (!nonIataRepository.existsById(nonIataId)) {
            throw new ResourceNotFoundException("NonIata", nonIataId);
        }

        // Multi-currency safety: ensure transactions do not mix different currencies
        List<String> currencies = salesTransactionRepository.findDistinctCurrenciesByNonIataId(nonIataId);
        if (currencies.size() > 1) {
            log.error("Non-IATA ID {} has transactions across multiple currencies: {}", nonIataId, currencies);
            throw new BadRequestException("Non-IATA account has sales transactions in multiple currencies: "
                    + currencies + ". Aggregation across currencies is strictly rejected.");
        }
        if (currencies.size() == 1 && !Constants.DEFAULT_CURRENCY.equalsIgnoreCase(currencies.get(0))) {
            log.error("Non-IATA ID {} has transactions in unsupported currency: {}", nonIataId, currencies.get(0));
            throw new BadRequestException("Non-IATA account has sales in unsupported currency: "
                    + currencies.get(0) + ". Only " + Constants.DEFAULT_CURRENCY + " is supported in V1.");
        }

        BigDecimal total = salesTransactionRepository.sumAmountByNonIataIdAndCurrency(nonIataId, Constants.DEFAULT_CURRENCY);
        return (total != null ? total : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int pageNumber = Math.max(0, pageable.getPageNumber());
        int pageSize = pageable.getPageSize() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "saleDate").and(Sort.by(Sort.Direction.DESC, "id"));
        } else {
            List<Sort.Order> safeOrders = new ArrayList<>();
            for (Sort.Order order : sort) {
                if (ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                    safeOrders.add(order);
                } else {
                    log.warn("Rejected invalid sales sort property: {}", order.getProperty());
                    throw new BadRequestException("Invalid sort property: '" + order.getProperty()
                            + "'. Allowed properties: " + ALLOWED_SORT_PROPERTIES);
                }
            }
            sort = Sort.by(safeOrders);
        }

        return PageRequest.of(pageNumber, pageSize, sort);
    }
}
