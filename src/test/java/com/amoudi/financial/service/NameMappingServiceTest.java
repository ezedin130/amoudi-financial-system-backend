package com.amoudi.financial.service;

import com.amoudi.financial.dto.NameMappingRequest;
import com.amoudi.financial.dto.NameMappingResponse;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.exception.DuplicateResourceException;
import com.amoudi.financial.exception.ResourceNotFoundException;
import com.amoudi.financial.model.NameMapping;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.NameMappingRepository;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NameMappingServiceTest {

    @Mock
    private NameMappingRepository nameMappingRepository;

    @Mock
    private NonIataRepository nonIataRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NameMappingServiceImpl nameMappingService;

    private NonIata sampleNonIata;
    private User sampleUser;
    private NameMapping sampleMapping;

    @BeforeEach
    void setUp() {
        sampleNonIata = NonIata.builder()
                .code("HAL01")
                .name("Halifa Travel")
                .status(NonIataStatus.ACTIVE)
                .build();
        sampleNonIata.setId(1L);

        sampleUser = User.builder()
                .username("finance_user")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build();
        sampleUser.setId(10L);

        sampleMapping = NameMapping.builder()
                .sourceName("INV*HALIFA CASH")
                .nonIata(sampleNonIata)
                .createdBy(sampleUser)
                .build();
        sampleMapping.setId(100L);
    }

    @Test
    @DisplayName("Resolve known agency name case-insensitively with trimming")
    void testResolveKnownAgencyName() {
        when(nameMappingRepository.findBySourceNameIgnoreCase("inv*halifa cash"))
                .thenReturn(Optional.of(sampleMapping));

        Optional<NonIata> result = nameMappingService.resolveNonIata("  inv*halifa cash  ");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Halifa Travel");
        assertThat(result.get().getCode()).isEqualTo("HAL01");
    }

    @Test
    @DisplayName("Resolve unknown agency name returns empty without guessing")
    void testResolveUnknownAgencyName() {
        when(nameMappingRepository.findBySourceNameIgnoreCase("INV UNKNOWN"))
                .thenReturn(Optional.empty());

        Optional<NonIata> result = nameMappingService.resolveNonIata("INV UNKNOWN");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Create mapping successfully")
    void testCreateMappingSuccess() {
        NameMappingRequest request = NameMappingRequest.builder()
                .sourceName("INV*HALIFA CASH")
                .nonIataId(1L)
                .build();

        when(nameMappingRepository.existsBySourceNameIgnoreCase("INV*HALIFA CASH")).thenReturn(false);
        when(nonIataRepository.findById(1L)).thenReturn(Optional.of(sampleNonIata));
        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
        when(nameMappingRepository.save(any(NameMapping.class))).thenReturn(sampleMapping);

        NameMappingResponse response = nameMappingService.createMapping(request, 10L);

        assertThat(response).isNotNull();
        assertThat(response.getSourceName()).isEqualTo("INV*HALIFA CASH");
        assertThat(response.getNonIataCode()).isEqualTo("HAL01");
        assertThat(response.getCreatedByUsername()).isEqualTo("finance_user");
    }

    @Test
    @DisplayName("Reject duplicate mapping creation for existing source name")
    void testCreateMappingDuplicateSourceName() {
        NameMappingRequest request = NameMappingRequest.builder()
                .sourceName("INV*HALIFA CASH")
                .nonIataId(1L)
                .build();

        when(nameMappingRepository.existsBySourceNameIgnoreCase("INV*HALIFA CASH")).thenReturn(true);

        assertThatThrownBy(() -> nameMappingService.createMapping(request, 10L))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(nameMappingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fail when Non-IATA account not found")
    void testCreateMappingNonIataNotFound() {
        NameMappingRequest request = NameMappingRequest.builder()
                .sourceName("INV*HALIFA CASH")
                .nonIataId(999L)
                .build();

        when(nameMappingRepository.existsBySourceNameIgnoreCase("INV*HALIFA CASH")).thenReturn(false);
        when(nonIataRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nameMappingService.createMapping(request, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("NonIata");
    }

    @Test
    @DisplayName("ensureMappingsForNonIata: registers both code and distinct name mappings")
    void testEnsureMappingsForNonIata_createsBothCodeAndName() {
        when(nameMappingRepository.existsBySourceNameIgnoreCase("HAL01")).thenReturn(false);
        when(nameMappingRepository.existsBySourceNameIgnoreCase("Halifa Travel")).thenReturn(false);
        when(nameMappingRepository.save(any(NameMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        nameMappingService.ensureMappingsForNonIata(sampleNonIata, sampleUser);

        // Verify save was called for both the code and the agency name
        verify(nameMappingRepository, times(2)).save(any(NameMapping.class));
    }

    @Test
    @DisplayName("ensureMappingsForNonIata: registers only code mapping when code equals name")
    void testEnsureMappingsForNonIata_singleMappingWhenCodeEqualsName() {
        NonIata sameCodeAndName = NonIata.builder()
                .code("SAME01")
                .name("SAME01")
                .status(NonIataStatus.ACTIVE)
                .build();

        when(nameMappingRepository.existsBySourceNameIgnoreCase("SAME01")).thenReturn(false);
        when(nameMappingRepository.save(any(NameMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        nameMappingService.ensureMappingsForNonIata(sameCodeAndName, sampleUser);

        verify(nameMappingRepository, times(1)).save(any(NameMapping.class));
    }

    @Test
    @DisplayName("ensureMappingsForNonIata: idempotent when mappings already exist")
    void testEnsureMappingsForNonIata_idempotentWhenAlreadyExists() {
        when(nameMappingRepository.existsBySourceNameIgnoreCase("HAL01")).thenReturn(true);
        when(nameMappingRepository.existsBySourceNameIgnoreCase("Halifa Travel")).thenReturn(true);

        nameMappingService.ensureMappingsForNonIata(sampleNonIata, sampleUser);

        // No new rows saved
        verify(nameMappingRepository, never()).save(any());
    }

    @Test
    @DisplayName("ensureMappingsForNonIata: falls back to existing user when creatorUser is null")
    void testEnsureMappingsForNonIata_fallbackUserResolution() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(sampleUser));
        when(nameMappingRepository.existsBySourceNameIgnoreCase("HAL01")).thenReturn(false);
        when(nameMappingRepository.existsBySourceNameIgnoreCase("Halifa Travel")).thenReturn(false);
        when(nameMappingRepository.save(any(NameMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        nameMappingService.ensureMappingsForNonIata(sampleNonIata, null);

        verify(userRepository).findByUsername("admin");
        verify(nameMappingRepository, times(2)).save(any(NameMapping.class));
    }
}
