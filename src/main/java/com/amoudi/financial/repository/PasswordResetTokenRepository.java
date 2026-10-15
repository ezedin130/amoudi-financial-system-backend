package com.amoudi.financial.repository;

import com.amoudi.financial.model.PasswordResetToken;
import com.amoudi.financial.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for PasswordResetToken entity.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Finds the most recent unconsumed recovery token for a specific user.
     */
    Optional<PasswordResetToken> findTopByUserAndConsumedAtIsNullOrderByCreatedAtDesc(User user);

    /**
     * Finds an unconsumed recovery token by the cryptographic hash of its reset token.
     */
    Optional<PasswordResetToken> findByResetTokenHashAndConsumedAtIsNull(String resetTokenHash);

    /**
     * Finds all unconsumed tokens for a user (used to invalidate prior active tokens).
     */
    List<PasswordResetToken> findByUserAndConsumedAtIsNull(User user);
}
