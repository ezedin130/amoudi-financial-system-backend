package com.amoudi.financial.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    @DisplayName("Bean validation failure should return HTTP 400 with VALIDATION_ERROR and details")
    void shouldReturnValidationErrors() throws Exception {
        String invalidPayload = """
                {
                    "name": "",
                    "email": "invalid-email-format",
                    "amount": -50.00
                }
                """;

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/test/validation"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details", hasItem(containsString("name: Name cannot be blank"))))
                .andExpect(jsonPath("$.details", hasItem(containsString("email: Invalid email format"))))
                .andExpect(jsonPath("$.details", hasItem(containsString("amount: Amount must be strictly positive"))));
    }

    @Test
    @WithMockUser
    @DisplayName("Malformed JSON should return HTTP 400 with MALFORMED_REQUEST")
    void shouldReturnMalformedRequestError() throws Exception {
        String malformedJson = "{ broken json here : ...";

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.path").value("/test/validation"));
    }

    @Test
    @WithMockUser
    @DisplayName("Type mismatch in path variable should return HTTP 400 with TYPE_MISMATCH")
    void shouldReturnTypeMismatchError() throws Exception {
        mockMvc.perform(get("/test/items/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.message").value(containsString("Parameter 'id' should be of type 'Long'")))
                .andExpect(jsonPath("$.path").value("/test/items/not-a-number"));
    }

    @Test
    @WithMockUser
    @DisplayName("BadRequestException should return HTTP 400 with BAD_REQUEST")
    void shouldReturnBadRequestError() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid test parameters provided"))
                .andExpect(jsonPath("$.path").value("/test/bad-request"));
    }

    @Test
    @WithMockUser
    @DisplayName("ResourceNotFoundException should return HTTP 404 with RESOURCE_NOT_FOUND")
    void shouldReturnResourceNotFoundError() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("TestEntity with identifier 'test-id-123' was not found"))
                .andExpect(jsonPath("$.path").value("/test/not-found"));
    }

    @Test
    @WithMockUser
    @DisplayName("AccessDeniedException should return HTTP 403 with ACCESS_DENIED")
    void shouldReturnAccessDeniedError() throws Exception {
        mockMvc.perform(get("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Access is denied"))
                .andExpect(jsonPath("$.path").value("/test/access-denied"));
    }

    @Test
    @WithMockUser
    @DisplayName("Unexpected server error should return HTTP 500 without leaking stack traces")
    void shouldReturnInternalServerErrorWithoutStackLeak() throws Exception {
        mockMvc.perform(get("/test/server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected internal error occurred. Please contact system support."))
                .andExpect(jsonPath("$.path").value("/test/server-error"));
    }

    @Test
    @DisplayName("Unauthenticated request to protected endpoint should return HTTP 401 with UNAUTHORIZED")
    void shouldReturnUnauthorizedForProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/protected-feature"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/v1/protected-feature"));
    }
}
