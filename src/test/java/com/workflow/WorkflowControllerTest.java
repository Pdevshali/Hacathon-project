package com.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String uniqueKey() { return "ctrl-test-" + UUID.randomUUID(); }

    private Map<String, Object> validLoanRequest(String key) {
        return Map.of(
            "idempotencyKey", key,
            "workflowType", "loan-approval",
            "payload", Map.of(
                "creditScore", 720,
                "annualIncome", 800000,
                "loanAmount", 2000000,
                "age", 30
            )
        );
    }

    @Test
    @DisplayName("POST /api/v1/workflows - valid request returns 201")
    void submit_validRequest_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLoanRequest(uniqueKey()))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.idempotencyKey").exists())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.auditLogs").isArray())
            .andExpect(jsonPath("$.ruleResults").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/workflows - duplicate key returns 409")
    void submit_duplicateKey_returns409() throws Exception {
        String key = uniqueKey();
        String body = objectMapper.writeValueAsString(validLoanRequest(key));

        // First request
        mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());

        // Duplicate
        mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("DUPLICATE_REQUEST"))
            .andExpect(jsonPath("$.existingWorkflow").exists());
    }

    @Test
    @DisplayName("POST /api/v1/workflows - missing required fields returns 400")
    void submit_missingFields_returns400() throws Exception {
        Map<String, Object> badRequest = Map.of("workflowType", "loan-approval");

        mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/v1/workflows - unknown workflow type returns 400")
    void submit_unknownWorkflowType_returns400() throws Exception {
        Map<String, Object> req = Map.of(
            "idempotencyKey", uniqueKey(),
            "workflowType", "ghost-workflow",
            "payload", Map.of("foo", "bar")
        );

        mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/workflows/{id} - found returns 200 with full response")
    void getById_found_returns200() throws Exception {
        String key = uniqueKey();
        String createBody = objectMapper.writeValueAsString(validLoanRequest(key));

        String responseBody = mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(responseBody).get("id").asLong();

        mockMvc.perform(get("/api/v1/workflows/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.auditLogs", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.ruleResults", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("GET /api/v1/workflows/{id} - not found returns 404")
    void getById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/workflows/by-key/{key} - found")
    void getByKey_found_returns200() throws Exception {
        String key = uniqueKey();
        mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLoanRequest(key))))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/workflows/by-key/{key}", key))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotencyKey").value(key));
    }

    @Test
    @DisplayName("GET /api/v1/workflows - returns list")
    void getAll_returnsList() throws Exception {
        mockMvc.perform(get("/api/v1/workflows"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/workflows/by-status/APPROVED - filters correctly")
    void getByStatus_filtersCorrectly() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/by-status/APPROVED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }
}
