package com.amoudi.financial.service;

import com.amoudi.financial.dto.AccountStatementResponse;
import com.amoudi.financial.dto.StatementTransactionResponse;
import com.amoudi.financial.enums.NonIataStatus;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock
    private NonIataRepository nonIataRepository;

    @Mock
    private SalesTransactionRepository salesTransactionRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private StatementServiceImpl statementService;

    private NonIata nonIata;

    @BeforeEach
    void setUp() {
        nonIata = NonIata.builder()
                .code("HAL01")
                .name("Halifa Travel Agency")
                .status(NonIataStatus.ACTIVE)
                .build();
        nonIata.setId(1L);
    }

    @Test
    @DisplayName("Statement calculates opening balance, running balances, and closing balance chronologically")
    void testGetStatementChronologicalOrderAndRunningBalance() {
        LocalDate dateFrom = LocalDate.of(2026, 9, 1);
        LocalDate dateTo = LocalDate.of(2026, 9, 30);

        when(nonIataRepository.findById(1L)).thenReturn(Optional.of(nonIata));

        // Prior to dateFrom: 50,000 sales, 10,000 payments -> opening balance = 40,000
        when(salesTransactionRepository.sumSalesBeforeDate(1L, dateFrom, "ETB"))
                .thenReturn(new BigDecimal("50000.00"));
        when(paymentRepository.sumPostedPaymentsBeforeDate(1L, dateFrom, "ETB"))
                .thenReturn(new BigDecimal("10000.00"));

        // Transactions in period:
        // Sep 5: Sale of 20,000
        SalesTransaction sale1 = SalesTransaction.builder()
                .id(101L)
                .nonIata(nonIata)
                .saleDate(LocalDate.of(2026, 9, 5))
                .ticketNumber("ET-1001")
                .passengerName("John Doe")
                .route("ADD-DXB")
                .amount(new BigDecimal("20000.00"))
                .currency("ETB")
                .build();

        // Sep 15: Sale of 30,000
        SalesTransaction sale2 = SalesTransaction.builder()
                .id(102L)
                .nonIata(nonIata)
                .saleDate(LocalDate.of(2026, 9, 15))
                .ticketNumber("ET-1002")
                .passengerName("Jane Smith")
                .route("ADD-JED")
                .amount(new BigDecimal("30000.00"))
                .currency("ETB")
                .build();

        // Sep 10: Payment of 15,000
        Payment payment1 = Payment.builder()
                .nonIata(nonIata)
                .paymentDate(LocalDate.of(2026, 9, 10))
                .amount(new BigDecimal("15000.00"))
                .receiptNumber("REC-001")
                .paymentReference("CASH-REF")
                .status(PaymentStatus.POSTED)
                .currency("ETB")
                .build();
        payment1.setId(201L);

        when(salesTransactionRepository.findAll(org.mockito.ArgumentMatchers.<Specification<SalesTransaction>>any(), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(sale1, sale2));
        when(paymentRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Payment>>any(), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(payment1));

        AccountStatementResponse response = statementService.getStatement(1L, dateFrom, dateTo);

        assertThat(response).isNotNull();
        assertThat(response.getNonIataId()).isEqualTo(1L);
        assertThat(response.getNonIataCode()).isEqualTo("HAL01");
        assertThat(response.getOpeningBalance()).isEqualByComparingTo("40000.00");
        assertThat(response.getTotalSales()).isEqualByComparingTo("50000.00");
        assertThat(response.getTotalPayments()).isEqualByComparingTo("15000.00");
        // Closing balance = 40,000 + 50,000 - 15,000 = 75,000
        assertThat(response.getClosingBalance()).isEqualByComparingTo("75000.00");
        assertThat(response.getTransactionCount()).isEqualTo(3);

        List<StatementTransactionResponse> txs = response.getTransactions();
        assertThat(txs).hasSize(3);

        // Transaction 1: Sep 5 Sale (+20,000) -> balance 60,000
        assertThat(txs.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(txs.get(0).getType()).isEqualTo(StatementTransactionType.SALE);
        assertThat(txs.get(0).getDebit()).isEqualByComparingTo("20000.00");
        assertThat(txs.get(0).getRunningBalance()).isEqualByComparingTo("60000.00");

        // Transaction 2: Sep 10 Payment (-15,000) -> balance 45,000
        assertThat(txs.get(1).getTransactionDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(txs.get(1).getType()).isEqualTo(StatementTransactionType.PAYMENT);
        assertThat(txs.get(1).getCredit()).isEqualByComparingTo("15000.00");
        assertThat(txs.get(1).getRunningBalance()).isEqualByComparingTo("45000.00");

        // Transaction 3: Sep 15 Sale (+30,000) -> balance 75,000
        assertThat(txs.get(2).getTransactionDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(txs.get(2).getType()).isEqualTo(StatementTransactionType.SALE);
        assertThat(txs.get(2).getDebit()).isEqualByComparingTo("30000.00");
        assertThat(txs.get(2).getRunningBalance()).isEqualByComparingTo("75000.00");
    }

    @Test
    @DisplayName("Statement handles same-date deterministic tie-break (SALE before PAYMENT)")
    void testSameDateTieBreakOrder() {
        LocalDate date = LocalDate.of(2026, 9, 10);
        when(nonIataRepository.findById(1L)).thenReturn(Optional.of(nonIata));

        when(salesTransactionRepository.sumSalesBeforeDate(1L, date, "ETB"))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRepository.sumPostedPaymentsBeforeDate(1L, date, "ETB"))
                .thenReturn(BigDecimal.ZERO);

        SalesTransaction sale = SalesTransaction.builder()
                .id(10L)
                .nonIata(nonIata)
                .saleDate(date)
                .ticketNumber("T-1")
                .amount(new BigDecimal("1000.00"))
                .currency("ETB")
                .build();

        Payment payment = Payment.builder()
                .nonIata(nonIata)
                .paymentDate(date)
                .amount(new BigDecimal("1000.00"))
                .status(PaymentStatus.POSTED)
                .currency("ETB")
                .build();
        payment.setId(20L);

        when(salesTransactionRepository.findAll(org.mockito.ArgumentMatchers.<Specification<SalesTransaction>>any(), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(sale));
        when(paymentRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Payment>>any(), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(payment));

        AccountStatementResponse response = statementService.getStatement(1L, date, date);

        List<StatementTransactionResponse> txs = response.getTransactions();
        assertThat(txs).hasSize(2);
        // SALE first, then PAYMENT
        assertThat(txs.get(0).getType()).isEqualTo(StatementTransactionType.SALE);
        assertThat(txs.get(0).getRunningBalance()).isEqualByComparingTo("1000.00");
        assertThat(txs.get(1).getType()).isEqualTo(StatementTransactionType.PAYMENT);
        assertThat(txs.get(1).getRunningBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Throws BadRequestException when dateFrom is after dateTo")
    void testInvalidDateRangeThrowsBadRequest() {
        when(nonIataRepository.findById(1L)).thenReturn(Optional.of(nonIata));

        LocalDate dateFrom = LocalDate.of(2026, 9, 30);
        LocalDate dateTo = LocalDate.of(2026, 9, 1);

        assertThatThrownBy(() -> statementService.getStatement(1L, dateFrom, dateTo))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Start date (dateFrom) cannot be after end date (dateTo)");
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when Non-IATA does not exist")
    void testNonIataNotFoundThrowsException() {
        when(nonIataRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statementService.getStatement(999L, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Non-IATA not found with ID: 999");
    }
}
