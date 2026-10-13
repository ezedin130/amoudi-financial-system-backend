package com.amoudi.financial.service;

import com.amoudi.financial.dto.AgingReportResponse;
import com.amoudi.financial.dto.AgingRowResponse;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.SalesTransaction;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.PaymentRepository;
import com.amoudi.financial.repository.SalesTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgingServiceTest {

    @Mock
    private NonIataRepository nonIataRepository;

    @Mock
    private SalesTransactionRepository salesTransactionRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private AgingServiceImpl agingService;

    private NonIata halifa;

    @BeforeEach
    void setUp() {
        halifa = NonIata.builder()
                .code("HAL01")
                .name("Halifa Travel Agency")
                .status(NonIataStatus.ACTIVE)
                .build();
        halifa.setId(1L);
    }

    @Test
    @DisplayName("FIFO aging distributes payments against oldest sales and reconciles with outstanding balance")
    void testFifoAgingDistributionAndReconciliation() {
        LocalDate asOfDate = LocalDate.of(2026, 9, 30);

        when(nonIataRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(halifa));

        // 3 Sales:
        // Sale 1: 150 days ago (2026-05-03) -> 100,000 (120+ days bucket)
        SalesTransaction sale1 = SalesTransaction.builder()
                .id(101L)
                .nonIata(halifa)
                .saleDate(asOfDate.minusDays(150))
                .amount(new BigDecimal("100000.00"))
                .currency("ETB")
                .build();

        // Sale 2: 45 days ago (2026-08-16) -> 200,000 (31-60 days bucket)
        SalesTransaction sale2 = SalesTransaction.builder()
                .id(102L)
                .nonIata(halifa)
                .saleDate(asOfDate.minusDays(45))
                .amount(new BigDecimal("200000.00"))
                .currency("ETB")
                .build();

        // Sale 3: 10 days ago (2026-09-20) -> 300,000 (0-30 days / Current bucket)
        SalesTransaction sale3 = SalesTransaction.builder()
                .id(103L)
                .nonIata(halifa)
                .saleDate(asOfDate.minusDays(10))
                .amount(new BigDecimal("300000.00"))
                .currency("ETB")
                .build();

        when(salesTransactionRepository.findSalesForAging(1L, asOfDate))
                .thenReturn(List.of(sale1, sale2, sale3));

        // Total payments = 250,000
        // FIFO applies: 100,000 to sale1 (fully settled), 150,000 to sale2 (50,000 unsettled in 31-60 bucket)
        // Sale 3 has 300,000 unsettled in Current bucket
        when(paymentRepository.sumPostedPaymentsUpToDate(1L, asOfDate, "ETB"))
                .thenReturn(new BigDecimal("250000.00"));

        AgingReportResponse response = agingService.getAgingReport(asOfDate, 1L, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getAsOfDate()).isEqualTo(asOfDate);
        assertThat(response.getTotalAccounts()).isEqualTo(1);
        assertThat(response.getRows()).hasSize(1);

        AgingRowResponse row = response.getRows().get(0);
        assertThat(row.getNonIataId()).isEqualTo(1L);
        assertThat(row.getCode()).isEqualTo("HAL01");
        assertThat(row.getCurrent()).isEqualByComparingTo("300000.00");
        assertThat(row.getDays31To60()).isEqualByComparingTo("50000.00");
        assertThat(row.getDays61To90()).isEqualByComparingTo("0.00");
        assertThat(row.getDays91To120()).isEqualByComparingTo("0.00");
        assertThat(row.getDays120Plus()).isEqualByComparingTo("0.00");
        assertThat(row.getTotalOutstanding()).isEqualByComparingTo("350000.00");

        // Sum of all aging buckets must equal total outstanding balance
        BigDecimal bucketSum = row.getCurrent()
                .add(row.getDays31To60())
                .add(row.getDays61To90())
                .add(row.getDays91To120())
                .add(row.getDays120Plus());
        assertThat(bucketSum).isEqualByComparingTo(row.getTotalOutstanding());

        // Report summary totals
        assertThat(response.getTotalCurrent()).isEqualByComparingTo("300000.00");
        assertThat(response.getTotalDays31To60()).isEqualByComparingTo("50000.00");
        assertThat(response.getTotalOutstanding()).isEqualByComparingTo("350000.00");
    }

    @Test
    @DisplayName("Zero payments leaves all sales in their respective age buckets")
    void testZeroPaymentsAging() {
        LocalDate asOfDate = LocalDate.of(2026, 9, 30);

        when(nonIataRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(halifa));

        SalesTransaction sale1 = SalesTransaction.builder()
                .id(101L)
                .nonIata(halifa)
                .saleDate(asOfDate.minusDays(150)) // 120+
                .amount(new BigDecimal("100000.00"))
                .currency("ETB")
                .build();

        SalesTransaction sale2 = SalesTransaction.builder()
                .id(102L)
                .nonIata(halifa)
                .saleDate(asOfDate.minusDays(45)) // 31-60
                .amount(new BigDecimal("200000.00"))
                .currency("ETB")
                .build();

        SalesTransaction sale3 = SalesTransaction.builder()
                .id(103L)
                .nonIata(halifa)
                .saleDate(asOfDate.minusDays(10)) // Current
                .amount(new BigDecimal("300000.00"))
                .currency("ETB")
                .build();

        when(salesTransactionRepository.findSalesForAging(1L, asOfDate))
                .thenReturn(List.of(sale1, sale2, sale3));
        when(paymentRepository.sumPostedPaymentsUpToDate(1L, asOfDate, "ETB"))
                .thenReturn(BigDecimal.ZERO);

        AgingReportResponse response = agingService.getAgingReport(asOfDate, 1L, null, null);

        AgingRowResponse row = response.getRows().get(0);
        assertThat(row.getCurrent()).isEqualByComparingTo("300000.00");
        assertThat(row.getDays31To60()).isEqualByComparingTo("200000.00");
        assertThat(row.getDays61To90()).isEqualByComparingTo("0.00");
        assertThat(row.getDays91To120()).isEqualByComparingTo("0.00");
        assertThat(row.getDays120Plus()).isEqualByComparingTo("100000.00");
        assertThat(row.getTotalOutstanding()).isEqualByComparingTo("600000.00");
    }

    @Test
    @DisplayName("Overpayment scenario maintains exact reconciliation")
    void testOverpaymentReconciliation() {
        LocalDate asOfDate = LocalDate.of(2026, 9, 30);

        when(nonIataRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(halifa));

        SalesTransaction sale = SalesTransaction.builder()
                .id(101L)
                .nonIata(halifa)
                .saleDate(asOfDate.minusDays(5))
                .amount(new BigDecimal("10000.00"))
                .currency("ETB")
                .build();

        when(salesTransactionRepository.findSalesForAging(1L, asOfDate))
                .thenReturn(List.of(sale));
        when(paymentRepository.sumPostedPaymentsUpToDate(1L, asOfDate, "ETB"))
                .thenReturn(new BigDecimal("15000.00")); // 5000 overpayment

        AgingReportResponse response = agingService.getAgingReport(asOfDate, 1L, null, null);

        AgingRowResponse row = response.getRows().get(0);
        assertThat(row.getTotalOutstanding()).isEqualByComparingTo("-5000.00");
        assertThat(row.getCurrent()).isEqualByComparingTo("-5000.00");

        BigDecimal bucketSum = row.getCurrent()
                .add(row.getDays31To60())
                .add(row.getDays61To90())
                .add(row.getDays91To120())
                .add(row.getDays120Plus());
        assertThat(bucketSum).isEqualByComparingTo(row.getTotalOutstanding());
    }
}
