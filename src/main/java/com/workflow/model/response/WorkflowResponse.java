package com.workflow.model.response;

import com.workflow.model.enums.WorkflowStage;
import com.workflow.model.enums.WorkflowStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class WorkflowResponse {
    private Long id;
    private String idempotencyKey;
    private String workflowType;
    private WorkflowStatus status;
    private WorkflowStage currentStage;
    private String decisionReason;
    private int retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private List<RuleResultResponse> ruleResults;
    private List<AuditLogResponse> auditLogs;
}
