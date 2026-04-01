package com.workflow.model.response;

import com.workflow.model.enums.WorkflowStage;
import com.workflow.model.enums.WorkflowStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogResponse {
    private Long id;
    private WorkflowStage stage;
    private WorkflowStatus statusBefore;
    private WorkflowStatus statusAfter;
    private String message;
    private String details;
    private String actor;
    private LocalDateTime timestamp;
}
