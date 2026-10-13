package com.amoudi.financial.service;

import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.dto.SalesFilterRequest;
import com.amoudi.financial.dto.SalesTransactionResponse;
import com.amoudi.financial.exception.BadRequestException;
import com.amoudi.financial.exception.ResourceNotFoundException;
import com.amoudi.financial.model.ImportBatch;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.SalesTransaction;
import com.amoudi.financial.repository.NonIataRepository;
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
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesServiceTest {

    @Mock
    private SalesTransactionRepository salesTransactionRepository;

    @Mock
    private NonIataRepository nonIataRepository;

    @InjectMocks
    private SalesServiceImpl salesService;

    private NonIata nonIata;
    private ImportBatch importBatch;
    private SalesTransaction sampleSale;

    @BeforeEach
    void setUp() {
        nonIata = NonIata.builder()
                .code("HALIFA")
                .name("Halifa Travel")
                .build();
        nonIata.setId(10L);

        importBatch = ImportBatch.builder()
                .fileName("bsp_report_202609.xlsx")
                .objectKey("s3://batches/bsp_report_202609.xlsx")
                .build();
        importBatch.setId(100L);

        sampleSale = SalesTransaction.builder()
                .id(1L)
                .nonIata(nonIata)
                .importBatch(importBatch)
                .ticketNumber("072-1234567890")
                .transactionReference("TXN-2026-001")
                .saleDate(LocalDate.of(2026, 9, 15))
                .passengerName("Ali Al-Amoudi")
                .origin("JED")
                .destination("ADD")
                .route("JED-ADD")
                .amount(new BigDecimal("25000.00"))
                .currency("ETB")
                .sourceAgencyName("HALIFA TOURS")
                .sourceRowNumber(15)
                .sourceTransactionKey("KEY-123")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("listSales: retrieves paginated sales within valid date range")
    void listSales_validDateRange() {
        SalesFilterRequest filter = SalesFilterRequest.builder()
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 30))
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<SalesTransaction> page = new PageImpl<>(List.of(sampleSale), pageable, 1);

        when(salesTransactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<SalesTransactionResponse> response = salesService.listSales(filter, pageable);

        assertThat(response.getContent()).hasSize(1);
        SalesTransactionResponse dto = response.getContent().get(0);
        assertThat(dto.getTicketNumber()).isEqualTo("072-1234567890");
        assertThat(dto.getNonIataCode()).isEqualTo("HALIFA");
        assertThat(dto.getAmount()).isEqualByComparingTo(new BigDecimal("25000.00"));
    }

    @Test
    @DisplayName("listSales: throws BadRequestException when startDate is after endDate")
    void listSales_invalidDateRange_throwsBadRequest() {
        SalesFilterRequest filter = SalesFilterRequest.builder()
                .startDate(LocalDate.of(2026, 9, 30))
                .endDate(LocalDate.of(2026, 9, 1))
                .build();

        assertThatThrownBy(() -> salesService.listSales(filter, PageRequest.of(0, 10)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Start date cannot be after end date");

        verify(salesTransactionRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("listSales: rejects invalid sort field")
    void listSales_invalidSort_throwsBadRequest() {
        Pageable invalidPageable = PageRequest.of(0, 10, Sort.by("hackedColumn"));

        assertThatThrownBy(() -> salesService.listSales(null, invalidPageable))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("hackedColumn");
    }

    @Test
    @DisplayName("listSales: clamps page size to 100")
    void listSales_clampsPageSize() {
        Pageable requestedPageable = PageRequest.of(0, 500);
        Page<SalesTransaction> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);

        when(salesTransactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        salesService.listSales(null, requestedPageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(salesTransactionRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("getSaleById: returns sales transaction response")
    void getSaleById_success() {
        when(salesTransactionRepository.findById(1L)).thenReturn(Optional.of(sampleSale));

        SalesTransactionResponse response = salesService.getSaleById(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getNonIataId()).isEqualTo(10L);
        assertThat(response.getNonIataName()).isEqualTo("Halifa Travel");
        assertThat(response.getImportBatchId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getSaleById: throws ResourceNotFoundException when ID does not exist")
    void getSaleById_notFound_throwsException() {
        when(salesTransactionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salesService.getSaleById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("calculateTotalSalesForNonIata: aggregates sales successfully in ETB")
    void calculateTotalSalesForNonIata_success() {
        when(nonIataRepository.existsById(10L)).thenReturn(true);
        when(salesTransactionRepository.findDistinctCurrenciesByNonIataId(10L)).thenReturn(List.of("ETB"));
        when(salesTransactionRepository.sumAmountByNonIataIdAndCurrency(10L, "ETB"))
                .thenReturn(new BigDecimal("400000.00"));

        BigDecimal total = salesService.calculateTotalSalesForNonIata(10L);

        assertThat(total).isEqualByComparingTo(new BigDecimal("400000.00"));
    }

    @Test
    @DisplayName("calculateTotalSalesForNonIata: returns 0.00 when account has no sales")
    void calculateTotalSalesForNonIata_noSales_returnsZero() {
        when(nonIataRepository.existsById(10L)).thenReturn(true);
        when(salesTransactionRepository.findDistinctCurrenciesByNonIataId(10L)).thenReturn(List.of());
        when(salesTransactionRepository.sumAmountByNonIataIdAndCurrency(10L, "ETB")).thenReturn(null);

        BigDecimal total = salesService.calculateTotalSalesForNonIata(10L);

        assertThat(total).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("calculateTotalSalesForNonIata: throws ResourceNotFoundException if account does not exist")
    void calculateTotalSalesForNonIata_notFound() {
        when(nonIataRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> salesService.calculateTotalSalesForNonIata(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("calculateTotalSalesForNonIata: strictly rejects aggregation across multiple currencies")
    void calculateTotalSalesForNonIata_multipleCurrencies_throwsException() {
        when(nonIataRepository.existsById(10L)).thenReturn(true);
        when(salesTransactionRepository.findDistinctCurrenciesByNonIataId(10L)).thenReturn(List.of("ETB", "USD"));

        assertThatThrownBy(() -> salesService.calculateTotalSalesForNonIata(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("multiple currencies");
    }

    @Test
    @DisplayName("calculateTotalSalesForNonIata: strictly rejects unsupported currency")
    void calculateTotalSalesForNonIata_unsupportedCurrency_throwsException() {
        when(nonIataRepository.existsById(10L)).thenReturn(true);
        when(salesTransactionRepository.findDistinctCurrenciesByNonIataId(10L)).thenReturn(List.of("USD"));

        assertThatThrownBy(() -> salesService.calculateTotalSalesForNonIata(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("unsupported currency");
    }
}
