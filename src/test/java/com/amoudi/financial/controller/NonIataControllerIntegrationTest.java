package com.amoudi.financial.controller;

import com.amoudi.financial.dto.NonIataCreateRequest;
import com.amoudi.financial.dto.NonIataStatusUpdateRequest;
import com.amoudi.financial.dto.NonIataUpdateRequest;
import com.amoudi.financial.enums.NonIataStatus;
import com.amoudi.financial.enums.UserRole;
import com.amoudi.financial.enums.UserStatus;
import com.amoudi.financial.model.NonIata;
import com.amoudi.financial.model.User;
import com.amoudi.financial.repository.NonIataRepository;
import com.amoudi.financial.repository.UserRepository;
import com.amoudi.financial.service.JwtService;
import com.amoudi.financial.utils.Constants;
import com.amoudi.financial.model.NameMapping;
import com.amoudi.financial.model.SalesTransaction;
import com.amoudi.financial.repository.ImportBatchErrorRepository;
import com.amoudi.financial.repository.ImportBatchRepository;
import com.amoudi.financial.repository.NameMappingRepository;
import com.amoudi.financial.repository.SalesTransactionRepository;
import com.amoudi.financial.service.NameMappingService;
import com.amoudi.financial.service.NonIataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NonIataControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NonIataRepository nonIataRepository;

    @Autowired
    private NameMappingRepository nameMappingRepository;

    @Autowired
    private NameMappingService nameMappingService;

    @Autowired
    private SalesTransactionRepository salesTransactionRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private ImportBatchErrorRepository importBatchErrorRepository;

    @Autowired
    private NonIataService nonIataService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String financeToken;
    private String gmToken;
    private User financeUser;
    private NonIata existingAccount;

    @BeforeEach
    void setUp() {
        salesTransactionRepository.deleteAll();
        importBatchErrorRepository.deleteAll();
        importBatchRepository.deleteAll();
        nameMappingRepository.deleteAll();
        nonIataRepository.deleteAll();
        userRepository.deleteAll();

        financeUser = userRepository.save(User.builder()
                .username("finance_usr")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .fullName("Finance User")
                .role(UserRole.FINANCE)
                .status(UserStatus.ACTIVE)
                .build());

        User gmUser = userRepository.save(User.builder()
                .username("gm_usr")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .fullName("General Manager User")
                .role(UserRole.GENERAL_MANAGER)
                .status(UserStatus.ACTIVE)
                .build());

        financeToken = jwtService.generateToken("finance_usr", financeUser.getId(), UserRole.FINANCE, 3600000);
        gmToken = jwtService.generateToken("gm_usr", gmUser.getId(), UserRole.GENERAL_MANAGER, 3600000);

        existingAccount = nonIataRepository.save(NonIata.builder()
                .code("SABA_WORLD")
                .name("Saba World Travel")
                .status(NonIataStatus.ACTIVE)
                .contactName("Bilal Ahmed")
                .phone("+967-1-444555")
                .email("bilal@saba-travel.ye")
                .notes("Key corporate sub-agent")
                .build());
    }

    // -------------------------------------------------------------
    // Scenario 1: POST /api/v1/non-iatas by FINANCE -> 201 Created
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 1: FINANCE user can create a Non-IATA account (201 Created)")
    void createNonIata_byFinance_returns201Created() throws Exception {
        NonIataCreateRequest request = NonIataCreateRequest.builder()
                .code("AL_BURAQ")
                .name("Al Buraq Travel")
                .contactName("Rashid Saleh")
                .phone("+967-1-111222")
                .email("rashid@buraq.ye")
                .notes("New partner")
                .build();

        mockMvc.perform(post(Constants.NON_IATAS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.code", is("AL_BURAQ")))
                .andExpect(jsonPath("$.data.name", is("Al Buraq Travel")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")))
                .andExpect(jsonPath("$.data.id").isNumber());

        assertThat(nonIataRepository.existsByCode("AL_BURAQ")).isTrue();
        assertThat(nameMappingRepository.existsBySourceNameIgnoreCase("AL_BURAQ")).isTrue();
        assertThat(nameMappingRepository.existsBySourceNameIgnoreCase("Al Buraq Travel")).isTrue();

        Optional<NonIata> resolvedByCode = nameMappingService.resolveNonIata("AL_BURAQ");
        assertThat(resolvedByCode).isPresent();
        assertThat(resolvedByCode.get().getCode()).isEqualTo("AL_BURAQ");

        Optional<NonIata> resolvedByName = nameMappingService.resolveNonIata("Al Buraq Travel");
        assertThat(resolvedByName).isPresent();
        assertThat(resolvedByName.get().getCode()).isEqualTo("AL_BURAQ");
    }

    // -------------------------------------------------------------
    // Scenario 2: POST /api/v1/non-iatas by GENERAL_MANAGER -> 403 Forbidden
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 2: GENERAL_MANAGER cannot create a Non-IATA account (403 Forbidden)")
    void createNonIata_byGeneralManager_returns403Forbidden() throws Exception {
        NonIataCreateRequest request = NonIataCreateRequest.builder()
                .code("QUEEN_SHEBA")
                .name("Queen Sheba Travel")
                .build();

        mockMvc.perform(post(Constants.NON_IATAS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("ACCESS_DENIED")));
    }

    // -------------------------------------------------------------
    // Scenario 3: POST /api/v1/non-iatas unauthenticated -> 401 Unauthorized
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 3: Unauthenticated user cannot create Non-IATA (401 Unauthorized)")
    void createNonIata_unauthenticated_returns401Unauthorized() throws Exception {
        NonIataCreateRequest request = NonIataCreateRequest.builder()
                .code("NO_AUTH_AGENCY")
                .name("No Auth Agency")
                .build();

        mockMvc.perform(post(Constants.NON_IATAS_API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    // -------------------------------------------------------------
    // Scenario 4: POST /api/v1/non-iatas duplicate code -> 409 Conflict
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 4: Duplicate Non-IATA code is rejected (409 Conflict)")
    void createNonIata_duplicateCode_returns409Conflict() throws Exception {
        NonIataCreateRequest request = NonIataCreateRequest.builder()
                .code("saba_world") // Lowercase variation of existing SABA_WORLD
                .name("Duplicate Saba")
                .build();

        mockMvc.perform(post(Constants.NON_IATAS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("DUPLICATE_RESOURCE")))
                .andExpect(jsonPath("$.message", containsString("SABA_WORLD")));
    }

    // -------------------------------------------------------------
    // Scenario 5: POST /api/v1/non-iatas missing required fields -> 400 Bad Request
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 5: Missing required fields is rejected with validation details (400 Bad Request)")
    void createNonIata_blankCodeOrName_returns400BadRequest() throws Exception {
        NonIataCreateRequest request = NonIataCreateRequest.builder()
                .code("")
                .name("")
                .build();

        mockMvc.perform(post(Constants.NON_IATAS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    // -------------------------------------------------------------
    // Scenario 6: POST /api/v1/non-iatas invalid email -> 400 Bad Request
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 6: Malformed email is rejected (400 Bad Request)")
    void createNonIata_invalidEmail_returns400BadRequest() throws Exception {
        NonIataCreateRequest request = NonIataCreateRequest.builder()
                .code("VALID_CODE")
                .name("Valid Name")
                .email("not-an-email")
                .build();

        mockMvc.perform(post(Constants.NON_IATAS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    // -------------------------------------------------------------
    // Scenario 7: GET /api/v1/non-iatas/{id} by FINANCE -> 200 OK
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 7: FINANCE user can retrieve single Non-IATA account details (200 OK)")
    void getNonIataById_byFinance_returns200Ok() throws Exception {
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + existingAccount.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(existingAccount.getId().intValue())))
                .andExpect(jsonPath("$.data.code", is("SABA_WORLD")))
                .andExpect(jsonPath("$.data.name", is("Saba World Travel")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    // -------------------------------------------------------------
    // Scenario 8: GET /api/v1/non-iatas/{id} by GENERAL_MANAGER -> 200 OK
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 8: GENERAL_MANAGER can retrieve single Non-IATA account details (200 OK)")
    void getNonIataById_byGeneralManager_returns200Ok() throws Exception {
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + existingAccount.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.code", is("SABA_WORLD")));
    }

    // -------------------------------------------------------------
    // Scenario 9: GET /api/v1/non-iatas/{id} unauthenticated -> 401 Unauthorized
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 9: Unauthenticated request cannot get Non-IATA details (401 Unauthorized)")
    void getNonIataById_unauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/" + existingAccount.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    // -------------------------------------------------------------
    // Scenario 10: GET /api/v1/non-iatas/{id} not found -> 404 Not Found
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 10: Non-existent ID returns 404 Not Found")
    void getNonIataById_notFound_returns404NotFound() throws Exception {
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX + "/999999")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("RESOURCE_NOT_FOUND")));
    }

    // -------------------------------------------------------------
    // Scenario 11: PUT /api/v1/non-iatas/{id} by FINANCE -> 200 OK
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 11: FINANCE user can update mutable Non-IATA details (200 OK)")
    void updateNonIata_byFinance_returns200Ok() throws Exception {
        NonIataUpdateRequest updateRequest = NonIataUpdateRequest.builder()
                .name("Saba World Aviation & Tours")
                .contactName("Bilal Ahmed Updated")
                .phone("+967-1-999888")
                .email("support@sabaworld.ye")
                .notes("Updated contract terms")
                .build();

        mockMvc.perform(put(Constants.NON_IATAS_API_PREFIX + "/" + existingAccount.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Saba World Aviation & Tours")))
                .andExpect(jsonPath("$.data.contactName", is("Bilal Ahmed Updated")))
                .andExpect(jsonPath("$.data.email", is("support@sabaworld.ye")))
                // Code remains immutable
                .andExpect(jsonPath("$.data.code", is("SABA_WORLD")));

        NonIata refreshed = nonIataRepository.findById(existingAccount.getId()).orElseThrow();
        assertThat(refreshed.getName()).isEqualTo("Saba World Aviation & Tours");
        assertThat(refreshed.getCode()).isEqualTo("SABA_WORLD");
    }

    // -------------------------------------------------------------
    // Scenario 12: PUT /api/v1/non-iatas/{id} by GENERAL_MANAGER -> 403 Forbidden
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 12: GENERAL_MANAGER cannot update Non-IATA details (403 Forbidden)")
    void updateNonIata_byGeneralManager_returns403Forbidden() throws Exception {
        NonIataUpdateRequest updateRequest = NonIataUpdateRequest.builder()
                .name("Unauthorized Update")
                .build();

        mockMvc.perform(put(Constants.NON_IATAS_API_PREFIX + "/" + existingAccount.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    // -------------------------------------------------------------
    // Scenario 13: PUT /api/v1/non-iatas/{id} unauthenticated -> 401 Unauthorized
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 13: Unauthenticated user cannot update Non-IATA details (401 Unauthorized)")
    void updateNonIata_unauthenticated_returns401Unauthorized() throws Exception {
        NonIataUpdateRequest updateRequest = NonIataUpdateRequest.builder()
                .name("Unauthorized Update")
                .build();

        mockMvc.perform(put(Constants.NON_IATAS_API_PREFIX + "/" + existingAccount.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    // -------------------------------------------------------------
    // Scenario 14: PUT /api/v1/non-iatas/{id} not found -> 404 Not Found
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 14: Updating non-existent Non-IATA returns 404 Not Found")
    void updateNonIata_notFound_returns404NotFound() throws Exception {
        NonIataUpdateRequest updateRequest = NonIataUpdateRequest.builder()
                .name("Ghost Agency")
                .build();

        mockMvc.perform(put(Constants.NON_IATAS_API_PREFIX + "/888888")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    // -------------------------------------------------------------
    // Scenario 15: PUT /api/v1/non-iatas/{id} validation failure -> 400 Bad Request
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 15: Updating with invalid fields returns 400 Bad Request")
    void updateNonIata_invalidFields_returns400BadRequest() throws Exception {
        NonIataUpdateRequest updateRequest = NonIataUpdateRequest.builder()
                .name("") // Blank name violates @NotBlank
                .build();

        mockMvc.perform(put(Constants.NON_IATAS_API_PREFIX + "/" + existingAccount.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    // -------------------------------------------------------------
    // Scenario 16: PATCH /api/v1/non-iatas/{id}/status by FINANCE -> 200 OK (deactivation)
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 16: FINANCE user can update status to INACTIVE (200 OK)")
    void patchStatus_byFinance_returns200Ok() throws Exception {
        NonIataStatusUpdateRequest statusRequest = NonIataStatusUpdateRequest.builder()
                .status(NonIataStatus.INACTIVE)
                .build();

        mockMvc.perform(patch(Constants.NON_IATAS_API_PREFIX + "/" + existingAccount.getId() + "/status")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("INACTIVE")));

        NonIata refreshed = nonIataRepository.findById(existingAccount.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(NonIataStatus.INACTIVE);
    }

    // -------------------------------------------------------------
    // Scenario 17: PATCH /api/v1/non-iatas/{id}/status by GENERAL_MANAGER -> 403 Forbidden
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 17: GENERAL_MANAGER cannot change Non-IATA status (403 Forbidden)")
    void patchStatus_byGeneralManager_returns403Forbidden() throws Exception {
        NonIataStatusUpdateRequest statusRequest = NonIataStatusUpdateRequest.builder()
                .status(NonIataStatus.INACTIVE)
                .build();

        mockMvc.perform(patch(Constants.NON_IATAS_API_PREFIX + "/" + existingAccount.getId() + "/status")
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    // -------------------------------------------------------------
    // Scenario 18: PATCH /api/v1/non-iatas/{id}/status unauthenticated -> 401 Unauthorized
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 18: Unauthenticated request cannot change status (401 Unauthorized)")
    void patchStatus_unauthenticated_returns401Unauthorized() throws Exception {
        NonIataStatusUpdateRequest statusRequest = NonIataStatusUpdateRequest.builder()
                .status(NonIataStatus.INACTIVE)
                .build();

        mockMvc.perform(patch(Constants.NON_IATAS_API_PREFIX + "/" + existingAccount.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    // -------------------------------------------------------------
    // Scenario 19: GET /api/v1/non-iatas with search and status filters
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 19: List Non-IATAs with search query and status filter (200 OK)")
    void listNonIatas_withSearchAndStatusFilter_returns200Ok() throws Exception {
        // Create second account
        nonIataRepository.save(NonIata.builder()
                .code("HADRAMOUT_AIR")
                .name("Hadramout Aviation")
                .status(NonIataStatus.INACTIVE)
                .build());

        // 1. Search matching "saba" (case-insensitive substring)
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .param("search", "SABA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].code", is("SABA_WORLD")));

        // 2. Filter by status=INACTIVE
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].code", is("HADRAMOUT_AIR")));

        // 3. Search matching "aviation" in name
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + gmToken)
                        .param("search", "aviation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].code", is("HADRAMOUT_AIR")));
    }

    // -------------------------------------------------------------
    // Scenario 20: GET /api/v1/non-iatas invalid sort property -> 400 Bad Request
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 20: Invalid sort field returns 400 Bad Request")
    void listNonIatas_invalidSortField_returns400BadRequest() throws Exception {
        mockMvc.perform(get(Constants.NON_IATAS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .param("sort", "disallowedColumn,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    // -------------------------------------------------------------
    // Scenario 21: DELETE /api/v1/non-iatas/{id} is strictly prohibited -> 405 Method Not Allowed
    // -------------------------------------------------------------
    @Test
    @DisplayName("Scenario 21: DELETE endpoint does not exist and returns 405 Method Not Allowed")
    void deleteNonIata_strictlyProhibited_returns405MethodNotAllowed() throws Exception {
        mockMvc.perform(delete(Constants.NON_IATAS_API_PREFIX + "/" + existingAccount.getId())
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status", is(405)))
                .andExpect(jsonPath("$.error", is("METHOD_NOT_ALLOWED")));

        // Verify account still exists untouched
        assertThat(nonIataRepository.existsById(existingAccount.getId())).isTrue();
    }

    // =========================================================================
    // PART 18: CODE-BASED NAME MAPPING & IMMEDIATE IMPORT VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("Part 18 Test 2: Auto-created mapping is immediately resolvable via NameMappingService by code and name")
    void testNonIataCodeMapping_resolvableViaNameMappingService() throws Exception {
        NonIataCreateRequest request = NonIataCreateRequest.builder()
                .code("NIR01")
                .name("Nile River Travel")
                .contactName("Hassan Ahmed")
                .build();

        mockMvc.perform(post(Constants.NON_IATAS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code", is("NIR01")));

        // 1. Resolve by exact code
        Optional<NonIata> byCode = nameMappingService.resolveNonIata("NIR01");
        assertThat(byCode).isPresent();
        assertThat(byCode.get().getCode()).isEqualTo("NIR01");
        assertThat(byCode.get().getName()).isEqualTo("Nile River Travel");

        // 2. Resolve by lowercase code (case-insensitive)
        Optional<NonIata> byLowerCode = nameMappingService.resolveNonIata("nir01");
        assertThat(byLowerCode).isPresent();
        assertThat(byLowerCode.get().getCode()).isEqualTo("NIR01");

        // 3. Resolve by agency name
        Optional<NonIata> byName = nameMappingService.resolveNonIata("Nile River Travel");
        assertThat(byName).isPresent();
        assertThat(byName.get().getCode()).isEqualTo("NIR01");
    }

    @Test
    @DisplayName("Part 18 Test 3: Mapping auto-registration is idempotent and does not create duplicates")
    void testNonIataCodeMapping_idempotency_doesNotDuplicate() throws Exception {
        NonIataCreateRequest request = NonIataCreateRequest.builder()
                .code("IDEM01")
                .name("Idempotent Travel Agency")
                .build();

        mockMvc.perform(post(Constants.NON_IATAS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        long countBefore = nameMappingRepository.count();

        // Retrieve created NonIata and call ensureMappingsForNonIata again explicitly
        NonIata created = nonIataRepository.findByCode("IDEM01").orElseThrow();
        nameMappingService.ensureMappingsForNonIata(created, financeUser);

        long countAfter = nameMappingRepository.count();
        assertThat(countAfter).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("Part 18 Test 4: Newly created Non-IATA immediately resolves during real Excel sales import without manual mapping")
    void testNonIataCodeMapping_allowsImmediateExcelImportWithoutManualMapping() throws Exception {
        // Step 1: Create fresh Non-IATA through the normal REST endpoint
        NonIataCreateRequest createReq = NonIataCreateRequest.builder()
                .code("AUTO01")
                .name("Auto Matched Agency")
                .contactName("Tariq Ali")
                .email("auto@matched.ye")
                .build();

        mockMvc.perform(post(Constants.NON_IATAS_API_PREFIX)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code", is("AUTO01")));

        NonIata createdAgency = nonIataRepository.findByCode("AUTO01").orElseThrow();

        // Step 2: Prepare a real Galileo/BSP Excel file where Form of Payment Free Text matches the code
        byte[] excelBytes = buildSingleRowExcel("AUTO01", "065 9999999999", "18-Sep", "TEST/AUTO PASSENGER", 45000.00, "PNRAUTO1");
        MockMultipartFile file = new MockMultipartFile("file", "galileo_auto_test.xlsx", "application/vnd.ms-excel", excelBytes);

        // Step 3: Upload the Excel file through POST /api/v1/imports
        mockMvc.perform(multipart(Constants.IMPORTS_API_PREFIX)
                        .file(file)
                        .header(Constants.HEADER_AUTHORIZATION, Constants.TOKEN_BEARER_PREFIX + financeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("IMPORTED")))
                .andExpect(jsonPath("$.data.totalRows", is(1)))
                .andExpect(jsonPath("$.data.successfulRows", is(1)))
                .andExpect(jsonPath("$.data.unmatchedRows", is(0)))
                .andExpect(jsonPath("$.data.duplicateRows", is(0)))
                .andExpect(jsonPath("$.data.failedRows", is(0)));

        // Step 4: Verify sales transaction exists and is linked to the newly created Non-IATA
        List<SalesTransaction> transactions = salesTransactionRepository.findByNonIataId(createdAgency.getId());
        assertThat(transactions).hasSize(1);
        SalesTransaction tx = transactions.get(0);
        assertThat(tx.getTicketNumber()).isEqualTo("065 9999999999");
        assertThat(tx.getNonIata().getId()).isEqualTo(createdAgency.getId());
        assertThat(tx.getNonIata().getCode()).isEqualTo("AUTO01");
        assertThat(tx.getNonIata().getName()).isEqualTo("Auto Matched Agency");
        assertThat(tx.getAmount()).isEqualByComparingTo("45000.00");
    }

    private byte[] buildSingleRowExcel(String agencyText, String ticketNo, String dateStr, String passenger, double amount, String pnr) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            // Sheet1: non-authoritative summary
            Sheet sheet1 = wb.createSheet("Summary");
            Row r0 = sheet1.createRow(0);
            r0.createCell(0).setCellValue("Summary Total");
            r0.createCell(1).setCellValue(amount);

            // Sheet2: authoritative Galileo/BSP sheet
            Sheet sheet2 = wb.createSheet("Sheet2");
            Row header = sheet2.createRow(0);
            header.createCell(0).setCellValue("Number");
            header.createCell(1).setCellValue("Issued");
            header.createCell(2).setCellValue("Passenger");
            header.createCell(3).setCellValue("Sector");
            header.createCell(4).setCellValue("G.TOT");
            header.createCell(5).setCellValue("Form of Payment Free Text");
            header.createCell(6).setCellValue("PNR");

            Row row1 = sheet2.createRow(1);
            row1.createCell(0).setCellValue(ticketNo);
            row1.createCell(1).setCellValue(dateStr);
            row1.createCell(2).setCellValue(passenger);
            row1.createCell(3).setCellValue("ADDDXBADD");
            row1.createCell(4).setCellValue(amount);
            row1.createCell(5).setCellValue(agencyText);
            row1.createCell(6).setCellValue(pnr);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }
}
