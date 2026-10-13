package com.amoudi.financial.service;

import com.amoudi.financial.dto.NonIataCreateRequest;
import com.amoudi.financial.dto.NonIataResponse;
import com.amoudi.financial.dto.NonIataStatusUpdateRequest;
import com.amoudi.financial.dto.NonIataUpdateRequest;
import com.amoudi.financial.dto.PageResponse;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.exception.BadRequestException;
import com.amoudi.financial.exception.DuplicateResourceException;
import com.amoudi.financial.exception.ResourceNotFoundException;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.repository.NonIataRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NonIataServiceTest {

    @Mock
    private NonIataRepository nonIataRepository;

    @Mock
    private com.amoudi.financial.repository.AuditLogRepository auditLogRepository;

    @Mock
    private NameMappingService nameMappingService;

    @Mock
    private com.amoudi.financial.repository.UserRepository userRepository;

    @InjectMocks
    private NonIataServiceImpl nonIataService;

    private NonIata sampleNonIata;

    @BeforeEach
    void setUp() {
        sampleNonIata = NonIata.builder()
                .code("AL_RAYYAN")
                .name("Al Rayyan Travel & Tourism")
                .status(NonIataStatus.ACTIVE)
                .contactName("Tariq Mansoor")
                .phone("+967-1-234567")
                .email("info@alrayyan-travel.ye")
                .notes("Key corporate sub-agent")
                .build();
        sampleNonIata.setId(101L);
        sampleNonIata.setCreatedAt(LocalDateTime.now().minusDays(5));
        sampleNonIata.setUpdatedAt(LocalDateTime.now().minusDays(1));
    }

    @Test
    @DisplayName("createNonIata: normalizes code to uppercase, trims values, and saves with ACTIVE status and auto-mapping")
    void createNonIata_success() {
        NonIataCreateRequest request = NonIataCreateRequest.builder()
                .code("  al_rayyan  ")
                .name("  Al Rayyan Travel & Tourism  ")
                .contactName("  Tariq Mansoor  ")
                .phone("  +967-1-234567  ")
                .email("  INFO@ALRAYYAN-TRAVEL.YE  ")
                .notes("  Key corporate sub-agent  ")
                .build();

        when(nonIataRepository.existsByCodeIgnoreCase("AL_RAYYAN")).thenReturn(false);
        when(nonIataRepository.save(any(NonIata.class))).thenAnswer(invocation -> {
            NonIata entity = invocation.getArgument(0);
            entity.setId(101L);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            return entity;
        });

        NonIataResponse response = nonIataService.createNonIata(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getCode()).isEqualTo("AL_RAYYAN");
        assertThat(response.getName()).isEqualTo("Al Rayyan Travel & Tourism");
        assertThat(response.getStatus()).isEqualTo(NonIataStatus.ACTIVE);
        assertThat(response.getContactName()).isEqualTo("Tariq Mansoor");
        assertThat(response.getEmail()).isEqualTo("info@alrayyan-travel.ye");

        ArgumentCaptor<NonIata> captor = ArgumentCaptor.forClass(NonIata.class);
        verify(nonIataRepository).save(captor.capture());
        NonIata captured = captor.getValue();
        assertThat(captured.getCode()).isEqualTo("AL_RAYYAN");
        assertThat(captured.getName()).isEqualTo("Al Rayyan Travel & Tourism");
        assertThat(captured.getStatus()).isEqualTo(NonIataStatus.ACTIVE);
        assertThat(captured.getEmail()).isEqualTo("info@alrayyan-travel.ye");

        // Verify NameMappingService.ensureMappingsForNonIata was invoked with the created entity
        verify(nameMappingService).ensureMappingsForNonIata(eq(captured), any());
    }

    @Test
    @DisplayName("createNonIata: propagates exception when mapping service fails (triggering rollback)")
    void createNonIata_whenMappingFails_propagatesException() {
        NonIataCreateRequest request = NonIataCreateRequest.builder()
                .code("AL_RAYYAN")
                .name("Al Rayyan Travel")
                .build();

        when(nonIataRepository.existsByCodeIgnoreCase("AL_RAYYAN")).thenReturn(false);
        when(nonIataRepository.save(any(NonIata.class))).thenAnswer(inv -> {
            NonIata entity = inv.getArgument(0);
            entity.setId(101L);
            return entity;
        });
        doThrow(new RuntimeException("Simulated mapping service failure"))
                .when(nameMappingService).ensureMappingsForNonIata(any(), any());

        assertThatThrownBy(() -> nonIataService.createNonIata(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated mapping service failure");
    }

    @Test
    @DisplayName("createNonIata: throws DuplicateResourceException when code already exists")
    void createNonIata_duplicateCode_throwsException() {
        NonIataCreateRequest request = NonIataCreateRequest.builder()
                .code("AL_RAYYAN")
                .name("Another Rayyan")
                .build();

        when(nonIataRepository.existsByCodeIgnoreCase("AL_RAYYAN")).thenReturn(true);

        assertThatThrownBy(() -> nonIataService.createNonIata(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("AL_RAYYAN");

        verify(nonIataRepository, never()).save(any());
    }

    @Test
    @DisplayName("getNonIataById: returns NonIataResponse when account exists")
    void getNonIataById_success() {
        when(nonIataRepository.findById(101L)).thenReturn(Optional.of(sampleNonIata));

        NonIataResponse response = nonIataService.getNonIataById(101L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getCode()).isEqualTo("AL_RAYYAN");
        assertThat(response.getStatus()).isEqualTo(NonIataStatus.ACTIVE);
    }

    @Test
    @DisplayName("getNonIataById: throws ResourceNotFoundException when ID does not exist")
    void getNonIataById_notFound_throwsException() {
        when(nonIataRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nonIataService.getNonIataById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("updateNonIata: updates mutable fields while keeping code strictly immutable")
    void updateNonIata_success() {
        NonIataUpdateRequest updateRequest = NonIataUpdateRequest.builder()
                .name("Al Rayyan Holidays Updated")
                .contactName("Ahmed Ali")
                .phone("+967-1-987654")
                .email("AHMED@ALRAYYAN.YE")
                .notes("Updated notes")
                .build();

        when(nonIataRepository.findById(101L)).thenReturn(Optional.of(sampleNonIata));
        when(nonIataRepository.save(any(NonIata.class))).thenAnswer(inv -> inv.getArgument(0));

        NonIataResponse response = nonIataService.updateNonIata(101L, updateRequest);

        assertThat(response.getName()).isEqualTo("Al Rayyan Holidays Updated");
        assertThat(response.getContactName()).isEqualTo("Ahmed Ali");
        assertThat(response.getPhone()).isEqualTo("+967-1-987654");
        assertThat(response.getEmail()).isEqualTo("ahmed@alrayyan.ye");
        assertThat(response.getNotes()).isEqualTo("Updated notes");
        // Verify code remained unchanged
        assertThat(response.getCode()).isEqualTo("AL_RAYYAN");
    }

    @Test
    @DisplayName("updateNonIata: throws ResourceNotFoundException when updating non-existent account")
    void updateNonIata_notFound_throwsException() {
        NonIataUpdateRequest updateRequest = NonIataUpdateRequest.builder()
                .name("New Name")
                .build();

        when(nonIataRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nonIataService.updateNonIata(999L, updateRequest))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(nonIataRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateStatus: changes status to INACTIVE")
    void updateStatus_toInactive_success() {
        NonIataStatusUpdateRequest statusRequest = NonIataStatusUpdateRequest.builder()
                .status(NonIataStatus.INACTIVE)
                .build();

        when(nonIataRepository.findById(101L)).thenReturn(Optional.of(sampleNonIata));
        when(nonIataRepository.save(any(NonIata.class))).thenAnswer(inv -> inv.getArgument(0));

        NonIataResponse response = nonIataService.updateStatus(101L, statusRequest);

        assertThat(response.getStatus()).isEqualTo(NonIataStatus.INACTIVE);
    }

    @Test
    @DisplayName("updateStatus: throws ResourceNotFoundException when account does not exist")
    void updateStatus_notFound_throwsException() {
        NonIataStatusUpdateRequest statusRequest = NonIataStatusUpdateRequest.builder()
                .status(NonIataStatus.INACTIVE)
                .build();

        when(nonIataRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nonIataService.updateStatus(999L, statusRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("listNonIatas: returns paginated list with default sorting when unsorted")
    void listNonIatas_defaultPagingAndSorting() {
        Pageable requestedPageable = PageRequest.of(0, 10);
        Page<NonIata> page = new PageImpl<>(List.of(sampleNonIata), requestedPageable, 1);

        when(nonIataRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<NonIataResponse> response = nonIataService.listNonIatas(null, null, requestedPageable);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getPageNumber()).isZero();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(nonIataRepository).findAll(any(Specification.class), captor.capture());
        Pageable usedPageable = captor.getValue();
        // Since requestedPageable was unsorted, it should default to createdAt DESC
        assertThat(usedPageable.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(usedPageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("listNonIatas: rejects invalid sort field with BadRequestException")
    void listNonIatas_invalidSortField_throwsBadRequest() {
        Pageable invalidPageable = PageRequest.of(0, 10, Sort.by("maliciousSqlInjectionField"));

        assertThatThrownBy(() -> nonIataService.listNonIatas(null, null, invalidPageable))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maliciousSqlInjectionField");

        verify(nonIataRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("listNonIatas: clamps page size exceeding 100 to 100")
    void listNonIatas_pageSizeExceedsMax_clampsTo100() {
        Pageable requestedPageable = PageRequest.of(0, 500);
        Page<NonIata> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);

        when(nonIataRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        nonIataService.listNonIatas(null, null, requestedPageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(nonIataRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }
}
