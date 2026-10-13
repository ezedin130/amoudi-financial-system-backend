package com.amoudi.financial.controller;

import com.amoudi.financial.utils.Constants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/health should return 200 OK with UP status and correlation ID header")
    void shouldReturnHealthStatusOk() throws Exception {
        mockMvc.perform(get(Constants.HEALTH_ENDPOINT)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists(Constants.CORRELATION_ID_HEADER))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("Amoudi Financial Management"))
                .andExpect(jsonPath("$.database").value("CONNECTED"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.environment").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/health with custom X-Correlation-ID should preserve it in response")
    void shouldPreserveCustomCorrelationId() throws Exception {
        String testCorrelationId = "test-corr-id-998877";

        mockMvc.perform(get(Constants.HEALTH_ENDPOINT)
                        .header(Constants.CORRELATION_ID_HEADER, testCorrelationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(Constants.CORRELATION_ID_HEADER, testCorrelationId))
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
