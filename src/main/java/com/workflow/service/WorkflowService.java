package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.audit.AuditService;
import com.workflow.config.WorkflowProperties;
import com.workflow.engine.executor.WorkflowExecutor;
import com.workflow.exception.DuplicateRequestException;
import com.workflow.exception.WorkflowNotFoundException;
import com.workflow.model.entity.AuditLog;
import com.workflow.model.entity.RuleEvaluationResult;
import com.workflow.model.entity.WorkflowRequest;
import com.workflow.model.enums.WorkflowStage;
import com.workflow.model.enums.WorkflowStatus;
import com.workflow.model.request.WorkflowSubmitRequest;
import com.workflow.model.response.AuditLogResponse;
import com.workflow.model.response.RuleResultResponse;
import com.workflow.model.response.WorkflowResponse;
import com.workflow.repository.AuditLogRepository;
import com.workflow.repository.RuleEvaluationResultRepository;
import com.workflow.repository.WorkflowRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRequestRepository requestRepo;
    private final AuditLogRepository auditLogRepo;
    private final RuleEvaluationResultRepository ruleResultRepo;
    private final WorkflowExecutor executor;
    private final AuditService auditService;
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Submits a new workflow request.
     * Enforces idempotency: duplicate idempotency keys return 409 with existing state.
     */
    @Transactional
    public WorkflowResponse submit(WorkflowSubmitRequest req) {
        // ── Idempotency check ──────────────────────────────────────────────────
        Optional<WorkflowRequest> existing = requestRepo.findByIdempotencyKey(req.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate submission detected for key={}", req.getIdempotencyKey());
            throw new DuplicateRequestException(
                "Request with idempotency key '" + req.getIdempotencyKey() + "' already submitted.",
                toResponse(existing.get()));
        }

        // ── Validate workflow type is registered ───────────────────────────────
        if (!properties.getConfigs().containsKey(req.getWorkflowType())) {
            throw new IllegalArgumentException(
                "Unknown workflow type: '" + req.getWorkflowType() +
                "'. Registered types: " + properties.getConfigs().keySet());
        }

        // ── Serialize payload ──────────────────────────────────────────────────
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(req.getPayload());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize payload: " + e.getMessage(), e);
        }

        // ── Persist initial record ─────────────────────────────────────────────
        WorkflowRequest request = WorkflowRequest.builder()
            .idempotencyKey(req.getIdempotencyKey())
            .workflowType(req.getWorkflowType())
            .status(WorkflowStatus.PENDING)
            .currentStage(WorkflowStage.VALIDATION)
            .payload(payloadJson)
            .retryCount(0)
            .build();

        request = requestRepo.save(request);
        auditService.log(request, WorkflowStage.VALIDATION,
            null, WorkflowStatus.PENDING,
            "Workflow submitted by user", null, "user");

        log.info("Workflow created: id={}, type={}, key={}",
            request.getId(), request.getWorkflowType(), request.getIdempotencyKey());

        // ── Execute synchronously (swap with async queue in production) ─────────
        executor.execute(request);

        return getById(request.getId());
    }

    public WorkflowResponse getById(Long id) {
        WorkflowRequest req = requestRepo.findById(id)
            .orElseThrow(() -> new WorkflowNotFoundException("Workflow not found with id: " + id));
        return toResponse(req);
    }

    public WorkflowResponse getByIdempotencyKey(String key) {
        WorkflowRequest req = requestRepo.findByIdempotencyKey(key)
            .orElseThrow(() -> new WorkflowNotFoundException(
                "No workflow found with idempotency key: " + key));
        return toResponse(req);
    }

    public List<WorkflowResponse> getAll() {
        return requestRepo.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    public List<WorkflowResponse> getByStatus(String status) {
        try {
            WorkflowStatus workflowStatus = WorkflowStatus.valueOf(status.toUpperCase());
            return requestRepo.findByStatus(workflowStatus).stream()
                .map(this::toResponse)
                .toList();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
    }

    // ── Mapping helpers ────────────────────────────────────────────────────────

    private WorkflowResponse toResponse(WorkflowRequest req) {
        List<AuditLog> logs = auditLogRepo
            .findByWorkflowRequestIdOrderByTimestampAsc(req.getId());
        List<RuleEvaluationResult> rules = ruleResultRepo
            .findByWorkflowRequestIdOrderByEvaluatedAtAsc(req.getId());

        return WorkflowResponse.builder()
            .id(req.getId())
            .idempotencyKey(req.getIdempotencyKey())
            .workflowType(req.getWorkflowType())
            .status(req.getStatus())
            .currentStage(req.getCurrentStage())
            .decisionReason(req.getDecisionReason())
            .retryCount(req.getRetryCount())
            .createdAt(req.getCreatedAt())
            .updatedAt(req.getUpdatedAt())
            .completedAt(req.getCompletedAt())
            .auditLogs(logs.stream().map(this::toAuditResponse).toList())
            .ruleResults(rules.stream().map(this::toRuleResponse).toList())
            .build();
    }

    private AuditLogResponse toAuditResponse(AuditLog l) {
        return AuditLogResponse.builder()
            .id(l.getId())
            .stage(l.getStage())
            .statusBefore(l.getStatusBefore())
            .statusAfter(l.getStatusAfter())
            .message(l.getMessage())
            .details(l.getDetails())
            .actor(l.getActor())
            .timestamp(l.getTimestamp())
            .build();
    }

    private RuleResultResponse toRuleResponse(RuleEvaluationResult r) {
        return RuleResultResponse.builder()
            .ruleId(r.getRuleId())
            .ruleName(r.getRuleName())
            .fieldEvaluated(r.getFieldEvaluated())
            .expectedValue(r.getExpectedValue())
            .actualValue(r.getActualValue())
            .operator(r.getOperator())
            .passed(r.isPassed())
            .failAction(r.getFailAction())
            .explanation(r.getExplanation())
            .evaluatedAt(r.getEvaluatedAt())
            .build();
    }
}
