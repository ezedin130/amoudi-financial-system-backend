package com.amoudi.financial.service;

import com.amoudi.financial.dto.*;
import com.amoudi.financial.enums.ImportErrorType;
import com.amoudi.financial.enums.ImportStatus;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.*;
import com.amoudi.financial.repository.*;
import com.amoudi.financial.service.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ExcelParserService excelParserService;

    @Mock
    private NameMappingService nameMappingService;

    @Mock
    private ImportBatchRepository importBatchRepository;

    @Mock
    private ImportBatchErrorRepository importBatchErrorRepository;

    @Mock
    private SalesTransactionRepository salesTransactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private ImportServiceImpl importService;

    private User financeUser;
    private NonIata halifa;
    private NonIata emran;

    @BeforeEach
    void setUp() {
        financeUser = User.builder()
                .username("finance_user")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build();
        financeUser.setId(1L);

        halifa = NonIata.builder()
                .code("HAL01")
                .name("Halifa Travel")
                .status(NonIataStatus.ACTIVE)
                .build();
        halifa.setId(10L);

        emran = NonIata.builder()
                .code("EMR01")
                .name("Emran Travel")
                .status(NonIataStatus.ACTIVE)
                .build();
        emran.setId(20L);
    }

    @Test
    @DisplayName("Successfully ingest report with multiple Non-IATAs and calculate metrics")
    void testUploadAndProcessSuccessfulMultiNonIata() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "SEP- 17.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy content".getBytes()
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(financeUser));
        when(fileStorageService.store(any(MockMultipartFile.class), eq("imports"))).thenReturn("imports/20260917/uuid_SEP- 17.xlsx");

        ImportBatch savedBatch = ImportBatch.builder()
                .fileName("SEP- 17.xlsx")
                .objectKey("imports/20260917/uuid_SEP- 17.xlsx")
                .uploadedBy(financeUser)
                .status(ImportStatus.PROCESSING)
                .build();
        savedBatch.setId(100L);
        when(importBatchRepository.save(any(ImportBatch.class))).thenReturn(savedBatch);

        ParsedSalesRow row1 = ParsedSalesRow.builder()
                .sourceRowNumber(2)
                .ticketNumber("065 5282705392")
                .saleDate(LocalDate.of(2026, 9, 17))
                .passengerName("HALIFA PASSENGER")
                .amount(new BigDecimal("119842.00"))
                .currency("ETB")
                .sourceAgencyName("INV*HALIFA CASH")
                .pnr("J3PPWN")
                .sourceTransactionKey("TICKET:0655282705392|DATE:2026-09-17|AMT:119842.00|PNR:J3PPWN")
                .build();

        ParsedSalesRow row2 = ParsedSalesRow.builder()
                .sourceRowNumber(3)
                .ticketNumber("071 5282705411")
                .saleDate(LocalDate.of(2026, 9, 17))
                .passengerName("EMRAN PASSENGER")
                .amount(new BigDecimal("74728.00"))
                .currency("ETB")
                .sourceAgencyName("INV EMRAN HIJRA")
                .pnr("J3YNJR")
                .sourceTransactionKey("TICKET:0715282705411|DATE:2026-09-17|AMT:74728.00|PNR:J3YNJR")
                .build();

        ParsedReport parsedReport = ParsedReport.builder()
                .sheetName("Sheet2")
                .reportDate(LocalDate.of(2026, 9, 17))
                .totalRowsDetected(2)
                .validRows(List.of(row1, row2))
                .parsingErrors(List.of())
                .build();

        when(excelParserService.parse(any(InputStream.class), eq("SEP- 17.xlsx"))).thenReturn(parsedReport);
        when(nameMappingService.resolveNonIata("INV*HALIFA CASH")).thenReturn(Optional.of(halifa));
        when(nameMappingService.resolveNonIata("INV EMRAN HIJRA")).thenReturn(Optional.of(emran));
        when(salesTransactionRepository.existsBySourceTransactionKey(anyString())).thenReturn(false);

        ImportBatchResponse response = importService.uploadAndProcess(file, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getStatus()).isEqualTo(ImportStatus.IMPORTED);
        assertThat(response.getTotalRows()).isEqualTo(2);
        assertThat(response.getSuccessfulRows()).isEqualTo(2);
        assertThat(response.getDuplicateRows()).isEqualTo(0);
        assertThat(response.getUnmatchedRows()).isEqualTo(0);

        // Verify sales transactions were saved with correct Non-IATA assignments
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SalesTransaction>> txCaptor = ArgumentCaptor.forClass(List.class);
        verify(salesTransactionRepository).saveAll(txCaptor.capture());
        List<SalesTransaction> savedTxs = txCaptor.getValue();
        assertThat(savedTxs).hasSize(2);

        SalesTransaction tx1 = savedTxs.get(0);
        assertThat(tx1.getNonIata().getId()).isEqualTo(10L); // Halifa
        assertThat(tx1.getAmount()).isEqualByComparingTo(new BigDecimal("119842.00"));

        SalesTransaction tx2 = savedTxs.get(1);
        assertThat(tx2.getNonIata().getId()).isEqualTo(20L); // Emran
        assertThat(tx2.getAmount()).isEqualByComparingTo(new BigDecimal("74728.00"));
    }

    @Test
    @DisplayName("Detect duplicate rows and prevent double ingestion")
    void testUploadAndProcessDuplicateDetection() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "SEP- 17.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy content".getBytes()
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(financeUser));
        when(fileStorageService.store(any(MockMultipartFile.class), eq("imports"))).thenReturn("imports/key");

        ImportBatch batch = ImportBatch.builder().build();
        batch.setId(101L);
        when(importBatchRepository.save(any(ImportBatch.class))).thenReturn(batch);

        ParsedSalesRow duplicateRow = ParsedSalesRow.builder()
                .sourceRowNumber(2)
                .ticketNumber("065 5282705392")
                .saleDate(LocalDate.of(2026, 9, 17))
                .amount(new BigDecimal("119842.00"))
                .sourceAgencyName("INV*HALIFA CASH")
                .sourceTransactionKey("TICKET:0655282705392|DATE:2026-09-17|AMT:119842.00|PNR:J3PPWN")
                .build();

        ParsedReport parsedReport = ParsedReport.builder()
                .sheetName("Sheet2")
                .reportDate(LocalDate.of(2026, 9, 17))
                .totalRowsDetected(1)
                .validRows(List.of(duplicateRow))
                .parsingErrors(List.of())
                .build();

        when(excelParserService.parse(any(), eq("SEP- 17.xlsx"))).thenReturn(parsedReport);
        when(nameMappingService.resolveNonIata("INV*HALIFA CASH")).thenReturn(Optional.of(halifa));
        // Simulate that transaction key already exists in database
        when(salesTransactionRepository.existsBySourceTransactionKey("TICKET:0655282705392|DATE:2026-09-17|AMT:119842.00|PNR:J3PPWN"))
                .thenReturn(true);

        ImportBatchResponse response = importService.uploadAndProcess(file, 1L);

        assertThat(response.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(response.getTotalRows()).isEqualTo(1);
        assertThat(response.getSuccessfulRows()).isEqualTo(0);
        assertThat(response.getDuplicateRows()).isEqualTo(1);

        verify(salesTransactionRepository, never()).saveAll(any());
        verify(importBatchErrorRepository).saveAll(any());
    }

    @Test
    @DisplayName("Record unmatched agency and do not guess Non-IATA")
    void testUploadAndProcessUnmatchedAgency() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "SEP- 17.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy content".getBytes()
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(financeUser));
        when(fileStorageService.store(any(), eq("imports"))).thenReturn("imports/key");

        ImportBatch batch = ImportBatch.builder().build();
        batch.setId(102L);
        when(importBatchRepository.save(any(ImportBatch.class))).thenReturn(batch);

        ParsedSalesRow unmatchedRow = ParsedSalesRow.builder()
                .sourceRowNumber(5)
                .ticketNumber("071 9999999999")
                .saleDate(LocalDate.of(2026, 9, 17))
                .amount(new BigDecimal("50000.00"))
                .sourceAgencyName("INV UNKNOWN TRAVEL")
                .sourceTransactionKey("TICKET:0719999999999|DATE:2026-09-17|AMT:50000.00|PNR:XYZ")
                .build();

        ParsedReport parsedReport = ParsedReport.builder()
                .sheetName("Sheet2")
                .reportDate(LocalDate.of(2026, 9, 17))
                .totalRowsDetected(1)
                .validRows(List.of(unmatchedRow))
                .parsingErrors(List.of())
                .build();

        when(excelParserService.parse(any(), eq("SEP- 17.xlsx"))).thenReturn(parsedReport);
        // Name mapping returns empty
        when(nameMappingService.resolveNonIata("INV UNKNOWN TRAVEL")).thenReturn(Optional.empty());

        ImportBatchResponse response = importService.uploadAndProcess(file, 1L);

        assertThat(response.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(response.getUnmatchedRows()).isEqualTo(1);
        assertThat(response.getSuccessfulRows()).isEqualTo(0);

        verify(salesTransactionRepository, never()).saveAll(any());
        verify(importBatchErrorRepository).saveAll(any());
    }
}
