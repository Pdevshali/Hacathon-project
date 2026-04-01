package com.workflow.audit;

import com.workflow.model.entity.AuditLog;
import com.workflow.model.entity.WorkflowRequest;
import com.workflow.model.enums.WorkflowStage;
import com.workflow.model.enums.WorkflowStatus;
import com.workflow.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Records every state transition or notable event in the workflow lifecycle.
     * Every call creates an immutable audit entry — never updated or deleted.
     */
    public AuditLog log(WorkflowRequest request,
                        WorkflowStage stage,
                        WorkflowStatus statusBefore,
                        WorkflowStatus statusAfter,
                        String message,
                        String details,
                        String actor) {

        AuditLog entry = AuditLog.builder()
            .workflowRequest(request)
            .stage(stage)
            .statusBefore(statusBefore)
            .statusAfter(statusAfter)
            .message(message)
            .details(details)
            .actor(actor)
            .build();

        AuditLog saved = auditLogRepository.save(entry);
        log.debug("AUDIT [key={}] stage={} {} -> {} | {}",
            request.getIdempotencyKey(), stage, statusBefore, statusAfter, message);
        return saved;
    }
}
