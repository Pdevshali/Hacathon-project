package com.workflow.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class WorkflowSubmitRequest {

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    @NotBlank(message = "Workflow type is required")
    private String workflowType;

    @NotNull(message = "Payload is required")
    private Map<String, Object> payload;
}
