package com.workflow.engine.executor;

import com.workflow.config.WorkflowProperties;
import com.workflow.exception.ExternalDependencyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalDependencyService {

    private final WorkflowProperties properties;
    private final Random random = new Random();

    /**
     * Simulates calling an external verification service.
     * Configured failure rate simulates real transient failures.
     */
    public ExternalCheckResult performCheck(String idempotencyKey, String workflowType) {
        double failRate = properties.getExternalDependency().getSimulateFailureRate();

        log.info("Calling external verification service for key={}, type={}",
            idempotencyKey, workflowType);

        // Simulate network latency
        try {
            Thread.sleep(200 + random.nextInt(300));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate transient external service failure
        if (random.nextDouble() < failRate) {
            log.warn("External service SIMULATED FAILURE for key={}", idempotencyKey);
            throw new ExternalDependencyException(
                "External verification service unavailable (simulated transient failure)");
        }

        // Simulate occasional risk flag (5% chance)
        boolean riskFlagged = random.nextDouble() < 0.05;
        String message = riskFlagged
            ? "External check: risk flag raised - requires manual review"
            : "External check: all clear, no risk indicators found";

        log.info("External check completed for key={}: riskFlagged={}", idempotencyKey, riskFlagged);
        return new ExternalCheckResult(!riskFlagged, message);
    }

    public record ExternalCheckResult(boolean passed, String message) {}
}
