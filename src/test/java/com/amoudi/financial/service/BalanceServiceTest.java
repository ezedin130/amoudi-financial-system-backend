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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private NonIataRepository nonIataRepository;

    @Mock
    private SalesService salesService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private SalesTransactionRepository salesTransactionRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private BalanceServiceImpl balanceService;

    private NonIata nonIata;

    @BeforeEach
    void setUp() {
        nonIata = NonIata.builder()
                .code("HALIFA")
                .name("Halifa Travel")
                .status(NonIataStatus.ACTIVE)
                .build();
        nonIata.setId(10L);
    }

    @Test
    @DisplayName("getFinancialSummary: returns correct totalSales, payments, and outstandingBalance = sales - payments")
    void getFinancialSummary_success() {
        when(nonIataRepository.findById(10L)).thenReturn(Optional.of(nonIata));
        when(salesService.calculateTotalSalesForNonIata(10L)).thenReturn(new BigDecimal("400000.00"));
        when(paymentService.calculateTotalPostedPaymentsForNonIata(10L)).thenReturn(new BigDecimal("150000.00"));

        FinancialSummaryResponse response = balanceService.getFinancialSummary(10L);

        assertThat(response).isNotNull();
        assertThat(response.getNonIataId()).isEqualTo(10L);
        assertThat(response.getNonIataCode()).isEqualTo("HALIFA");
        assertThat(response.getCode()).isEqualTo("HALIFA");
        assertThat(response.getNonIataName()).isEqualTo("Halifa Travel");
        assertThat(response.getName()).isEqualTo("Halifa Travel");
        assertThat(response.getTotalSales()).isEqualByComparingTo(new BigDecimal("400000.00"));
        assertThat(response.getTotalPayments()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(response.getOutstandingBalance()).isEqualByComparingTo(new BigDecimal("250000.00"));
        assertThat(response.getCurrency()).isEqualTo("ETB");
    }

    @Test
    @DisplayName("getFinancialSummary: throws ResourceNotFoundException when Non-IATA is not found")
    void getFinancialSummary_notFound_throwsException() {
        when(nonIataRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.getFinancialSummary(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("getFinancialSummary: returns 0.00 balance when Non-IATA has no sales and no payments")
    void getFinancialSummary_noSales_returnsZero() {
        when(nonIataRepository.findById(10L)).thenReturn(Optional.of(nonIata));
        when(salesService.calculateTotalSalesForNonIata(10L)).thenReturn(BigDecimal.ZERO);
        when(paymentService.calculateTotalPostedPaymentsForNonIata(10L)).thenReturn(BigDecimal.ZERO);

        FinancialSummaryResponse response = balanceService.getFinancialSummary(10L);

        assertThat(response.getTotalSales()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(response.getTotalPayments()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(response.getOutstandingBalance()).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("listFinancialSummaries: paginated projection list mapped to responses with payments")
    void listFinancialSummaries_success() {
        NonIataFinancialProjection projection = new NonIataFinancialProjection() {
            @Override
            public Long getNonIataId() { return 10L; }
            @Override
            public String getCode() { return "HALIFA"; }
            @Override
            public String getName() { return "Halifa Travel"; }
            @Override
            public NonIataStatus getStatus() { return NonIataStatus.ACTIVE; }
            @Override
            public BigDecimal getTotalSales() { return new BigDecimal("600000.00"); }
            @Override
            public BigDecimal getTotalPayments() { return new BigDecimal("150000.00"); }
        };

        Page<NonIataFinancialProjection> page = new PageImpl<>(List.of(projection), PageRequest.of(0, 10), 1);
        when(nonIataRepository.findFinancialSummaries(eq("%halifa%"), eq(NonIataStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<FinancialSummaryResponse> response = balanceService.listFinancialSummaries(
                "HALIFA",
                NonIataStatus.ACTIVE,
                PageRequest.of(0, 10)
        );

        assertThat(response.getContent()).hasSize(1);
        FinancialSummaryResponse item = response.getContent().get(0);
        assertThat(item.getNonIataCode()).isEqualTo("HALIFA");
        assertThat(item.getTotalSales()).isEqualByComparingTo(new BigDecimal("600000.00"));
        assertThat(item.getTotalPayments()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(item.getOutstandingBalance()).isEqualByComparingTo(new BigDecimal("450000.00"));
    }

    @Test
    @DisplayName("listFinancialSummaries: rejects invalid sort field")
    void listFinancialSummaries_invalidSort() {
        Pageable invalidPageable = PageRequest.of(0, 10, Sort.by("secretField"));

        assertThatThrownBy(() -> balanceService.listFinancialSummaries(null, null, invalidPageable))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("secretField");
    }

    @Test
    @DisplayName("listFinancialSummaries: maps sorting on outstandingBalance to totalSales")
    void listFinancialSummaries_sortOutstandingBalance_mapsToTotalSales() {
        Pageable requestedPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "outstandingBalance"));
        Page<NonIataFinancialProjection> emptyPage = new PageImpl<>(List.of(), requestedPageable, 0);

        when(nonIataRepository.findFinancialSummaries(any(), any(), any(Pageable.class))).thenReturn(emptyPage);

        balanceService.listFinancialSummaries(null, null, requestedPageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(nonIataRepository).findFinancialSummaries(any(), any(), captor.capture());
        Pageable used = captor.getValue();
        assertThat(used.getSort().getOrderFor("totalSales")).isNotNull();
        assertThat(used.getSort().getOrderFor("totalSales").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("getCompanyFinancialOverview: returns totalSales, totalPayments, and outstandingBalance")
    void getCompanyFinancialOverview_success() {
        when(salesTransactionRepository.sumTotalSalesByCurrency("ETB")).thenReturn(new BigDecimal("1250000.50"));
        when(paymentRepository.sumTotalPostedPaymentsByCurrency("ETB")).thenReturn(new BigDecimal("450000.25"));
        when(nonIataRepository.countByStatus(NonIataStatus.ACTIVE)).thenReturn(5L);
        when(nonIataRepository.count()).thenReturn(6L);

        CompanyFinancialOverviewResponse overview = balanceService.getCompanyFinancialOverview();

        assertThat(overview).isNotNull();
        assertThat(overview.getTotalSales()).isEqualByComparingTo(new BigDecimal("1250000.50"));
        assertThat(overview.getTotalPayments()).isEqualByComparingTo(new BigDecimal("450000.25"));
        assertThat(overview.getOutstandingBalance()).isEqualByComparingTo(new BigDecimal("800000.25"));
        assertThat(overview.getActiveAccountsCount()).isEqualTo(5L);
        assertThat(overview.getTotalAccountsCount()).isEqualTo(6L);
        assertThat(overview.getCurrency()).isEqualTo("ETB");
    }
}
