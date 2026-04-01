package com.workflow.engine.executor;

import com.workflow.audit.AuditService;
import com.workflow.config.WorkflowProperties;
import com.workflow.engine.rule.RuleEvaluator;
import com.workflow.exception.ExternalDependencyException;
import com.workflow.model.entity.WorkflowRequest;
import com.workflow.model.enums.WorkflowStage;
import com.workflow.model.enums.WorkflowStatus;
import com.workflow.repository.WorkflowRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowExecutor {

    private final WorkflowRequestRepository requestRepo;
    private final RuleEvaluator ruleEvaluator;
    private final ExternalDependencyService externalDependencyService;
    private final AuditService auditService;
    private final WorkflowProperties properties;

    /**
     * Executes the full workflow pipeline synchronously.
     * Stages: VALIDATION → RULE_EVALUATION → EXTERNAL_CHECK → DECISION
     */
    public void execute(WorkflowRequest request) {
        log.info("Starting execution: id={}, type={}, key={}",
            request.getId(), request.getWorkflowType(), request.getIdempotencyKey());

        try {
            // ─── Stage 1: VALIDATION ───────────────────────────────────────────
            transitionStage(request, WorkflowStage.VALIDATION, WorkflowStatus.IN_PROGRESS,
                "Validation stage started");
            validateWorkflowConfig(request);

            // ─── Stage 2: RULE_EVALUATION ──────────────────────────────────────
            transitionStage(request, WorkflowStage.RULE_EVALUATION, WorkflowStatus.IN_PROGRESS,
                "Rule evaluation started");
            RuleEvaluator.RuleEvaluationOutcome outcome = ruleEvaluator.evaluate(request);

            if (!outcome.allPassed()) {
                WorkflowStatus finalStatus = switch (outcome.failAction()) {
                    case REJECT -> WorkflowStatus.REJECTED;
                    case MANUAL_REVIEW -> WorkflowStatus.MANUAL_REVIEW;
                    default -> WorkflowStatus.REJECTED;
                };
                complete(request, finalStatus,
                    "Rule evaluation failed: " + outcome.reason());
                return;
            }

            auditService.log(request, WorkflowStage.RULE_EVALUATION,
                WorkflowStatus.IN_PROGRESS, WorkflowStatus.IN_PROGRESS,
                "All " + outcome.results().size() + " rules passed successfully",
                null, "system");

            // ─── Stage 3: EXTERNAL_CHECK ───────────────────────────────────────
            transitionStage(request, WorkflowStage.EXTERNAL_CHECK, WorkflowStatus.IN_PROGRESS,
                "External dependency check initiated");
            ExternalDependencyService.ExternalCheckResult extResult =
                externalDependencyService.performCheck(
                    request.getIdempotencyKey(), request.getWorkflowType());

            if (!extResult.passed()) {
                complete(request, WorkflowStatus.MANUAL_REVIEW,
                    "External check flagged: " + extResult.message());
                return;
            }

            auditService.log(request, WorkflowStage.EXTERNAL_CHECK,
                WorkflowStatus.IN_PROGRESS, WorkflowStatus.IN_PROGRESS,
                "External check passed: " + extResult.message(), null, "system");

            // ─── Stage 4: DECISION ─────────────────────────────────────────────
            transitionStage(request, WorkflowStage.DECISION, WorkflowStatus.IN_PROGRESS,
                "Decision stage: all checks passed, preparing approval");
            complete(request, WorkflowStatus.APPROVED,
                "All rules passed. " + extResult.message());

        } catch (ExternalDependencyException e) {
            handleExternalFailure(request, e);
        } catch (IllegalArgumentException e) {
            log.warn("Validation failure for id={}: {}", request.getId(), e.getMessage());
            complete(request, WorkflowStatus.REJECTED, "Validation failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing workflow id={}", request.getId(), e);
            complete(request, WorkflowStatus.FAILED, "Internal error: " + e.getMessage());
        }
    }

    private void validateWorkflowConfig(WorkflowRequest request) {
        WorkflowProperties.WorkflowConfig config =
            properties.getConfigs().get(request.getWorkflowType());
        if (config == null) {
            throw new IllegalArgumentException(
                "No configuration found for workflow type: " + request.getWorkflowType());
        }
        if (config.getRules() == null || config.getRules().isEmpty()) {
            throw new IllegalArgumentException(
                "Workflow type '" + request.getWorkflowType() + "' has no rules configured");
        }
        auditService.log(request, WorkflowStage.VALIDATION,
            WorkflowStatus.IN_PROGRESS, WorkflowStatus.IN_PROGRESS,
            "Config validated: " + config.getRules().size() + " rules loaded for '"
                + config.getName() + "'",
            null, "system");
    }

    private void handleExternalFailure(WorkflowRequest request, ExternalDependencyException e) {
        WorkflowProperties.WorkflowConfig config =
            properties.getConfigs().get(request.getWorkflowType());
        int maxRetries = config != null ? config.getMaxRetries() : 3;
        long delayMs = config != null ? config.getRetryDelayMs() : 1000;

        if (request.getRetryCount() < maxRetries) {
            int attempt = request.getRetryCount() + 1;
            request.setRetryCount(attempt);
            request.setStatus(WorkflowStatus.RETRYING);
            requestRepo.save(request);

            auditService.log(request, WorkflowStage.EXTERNAL_CHECK,
                WorkflowStatus.IN_PROGRESS, WorkflowStatus.RETRYING,
                String.format("External service failure — retry %d/%d scheduled in %dms",
                    attempt, maxRetries, delayMs),
                e.getMessage(), "system");

            log.warn("Retrying workflow id={}, attempt={}/{}", request.getId(), attempt, maxRetries);

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            // Re-enter execution from external check stage
            executeFromExternalCheck(request);
        } else {
            complete(request, WorkflowStatus.FAILED,
                "External service failed after " + maxRetries + " retries: " + e.getMessage());
        }
    }

    /**
     * Re-entry point for retry — skips validation and rule evaluation,
     * goes directly to the external check stage.
     */
    private void executeFromExternalCheck(WorkflowRequest request) {
        try {
            transitionStage(request, WorkflowStage.EXTERNAL_CHECK, WorkflowStatus.IN_PROGRESS,
                "Retrying external check (attempt " + request.getRetryCount() + ")");

            ExternalDependencyService.ExternalCheckResult extResult =
                externalDependencyService.performCheck(
                    request.getIdempotencyKey(), request.getWorkflowType());

            if (!extResult.passed()) {
                complete(request, WorkflowStatus.MANUAL_REVIEW,
                    "External check flagged after retry: " + extResult.message());
                return;
            }

            transitionStage(request, WorkflowStage.DECISION, WorkflowStatus.IN_PROGRESS,
                "Decision stage after successful retry");
            complete(request, WorkflowStatus.APPROVED,
                "Approved after retry. " + extResult.message());

        } catch (ExternalDependencyException e) {
            handleExternalFailure(request, e);
        } catch (Exception e) {
            log.error("Error during retry for id={}", request.getId(), e);
            complete(request, WorkflowStatus.FAILED, "Failed during retry: " + e.getMessage());
        }
    }

    private void transitionStage(WorkflowRequest request, WorkflowStage stage,
                                   WorkflowStatus status, String message) {
        WorkflowStatus prev = request.getStatus();
        request.setCurrentStage(stage);
        request.setStatus(status);
        requestRepo.save(request);
        auditService.log(request, stage, prev, status, message, null, "system");
        log.debug("Stage transition [{}]: {} -> {} | {}", stage, prev, status, message);
    }

    private void complete(WorkflowRequest request, WorkflowStatus finalStatus, String reason) {
        WorkflowStatus prev = request.getStatus();
        request.setStatus(finalStatus);
        request.setCurrentStage(WorkflowStage.COMPLETED);
        request.setDecisionReason(reason);
        request.setCompletedAt(LocalDateTime.now());
        requestRepo.save(request);
        auditService.log(request, WorkflowStage.COMPLETED, prev, finalStatus,
            "Workflow completed with status: " + finalStatus.name(), reason, "system");
        log.info("Workflow id={} COMPLETED: status={}, reason={}", request.getId(), finalStatus, reason);
    }
}
