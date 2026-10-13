package com.amoudi.financial.dto;

import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Safe authenticated user profile representation.
 * Explicitly excludes password, passwordHash, and internal credentials.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserResponse {

    private Long id;
    private String username;
    private String fullName;
    private UserRole role;
    private UserStatus status;
}
