package com.amoudi.financial.repository;

import com.amoudi.financial.enums.ImportStatus;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.ImportBatch;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.SalesTransaction;
import com.amoudi.financial.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Database Integrity Verification (Part 10 - Pillar 9):
 * Verifies foreign key constraints, deletion restrictions, and orphan prevention.
 */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseIntegrityIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NonIataRepository nonIataRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private SalesTransactionRepository salesTransactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    @DisplayName("Foreign key constraint: Cannot delete Non-IATA referenced by SalesTransaction")
    void testCannotDeleteNonIataWithSalesTransactions() {
        User user = userRepository.saveAndFlush(User.builder()
                .username("db_user_1")
                .passwordHash("hash")
                .fullName("DB User")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        NonIata nonIata = nonIataRepository.saveAndFlush(NonIata.builder()
                .code("DEL_RESTRICT")
                .name("Protected Agency")
                .status(NonIataStatus.ACTIVE)
                .build());

        ImportBatch batch = importBatchRepository.saveAndFlush(ImportBatch.builder()
                .fileName("test.xlsx")
                .objectKey("imports/test_db_" + UUID.randomUUID())
                .uploadedBy(user)
                .status(ImportStatus.IMPORTED)
                .build());

        salesTransactionRepository.saveAndFlush(SalesTransaction.builder()
                .nonIata(nonIata)
                .importBatch(batch)
                .ticketNumber("065 0000000001")
                .amount(new BigDecimal("12000.00"))
                .currency("ETB")
                .saleDate(LocalDate.now())
                .sourceAgencyName("TEST_AGENCY")
                .sourceRowNumber(1)
                .sourceTransactionKey("TX_DEL_001")
                .build());

        // Attempt direct SQL deletion of parent NonIata -> must throw foreign key restriction
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM non_iatas WHERE id = ?", nonIata.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("Foreign key constraint: Cannot insert SalesTransaction with nonexistent Non-IATA ID")
    void testCannotInsertSalesWithNonexistentNonIata() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO sales_transactions (non_iata_id, ticket_number, amount, currency, sale_date, source_agency_name, source_row_number, source_transaction_key) " +
                        "VALUES (999999, '065 9999999999', 1000.00, 'ETB', CURRENT_DATE, 'GHOST_AGENCY', 1, 'GHOST_TX_KEY')"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
