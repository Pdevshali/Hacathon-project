package com.workflow;

import com.workflow.exception.DuplicateRequestException;
import com.workflow.model.enums.WorkflowStatus;
import com.workflow.model.request.WorkflowSubmitRequest;
import com.workflow.model.response.WorkflowResponse;
import com.workflow.service.WorkflowService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowServiceTest {

    @Autowired
    private WorkflowService workflowService;

    // ── Helper ─────────────────────────────────────────────────────────────────
    private String uniqueKey() {
        return "test-" + UUID.randomUUID();
    }

    private WorkflowSubmitRequest loanRequest(String key, int creditScore,
                                               int income, int loanAmount, int age) {
        var req = new WorkflowSubmitRequest();
        req.setIdempotencyKey(key);
        req.setWorkflowType("loan-approval");
        req.setPayload(Map.of(
            "creditScore", creditScore,
            "annualIncome", income,
            "loanAmount", loanAmount,
            "age", age
        ));
        return req;
    }

    // ── Happy Path ─────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Happy Path: Strong loan applicant should be APPROVED or MANUAL_REVIEW (external)")
    void happyPath_loanApproval_strongApplicant() {
        WorkflowResponse resp = workflowService.submit(
            loanRequest(uniqueKey(), 750, 1200000, 2000000, 35));

        assertNotNull(resp);
        assertNotNull(resp.getId());
        assertNotEquals(WorkflowStatus.PENDING, resp.getStatus());
        assertNotEquals(WorkflowStatus.IN_PROGRESS, resp.getStatus());

        // All rules should pass
        boolean allRulesPassed = resp.getRuleResults().stream().allMatch(r -> r.isPassed());
        assertTrue(allRulesPassed, "All rules should pass for a strong applicant");

        // Audit log should have multiple entries
        assertTrue(resp.getAuditLogs().size() >= 4, "Should have at least 4 audit entries");

        System.out.println("=== HAPPY PATH RESULT ===");
        System.out.println("Status: " + resp.getStatus());
        System.out.println("Reason: " + resp.getDecisionReason());
        System.out.println("Retries: " + resp.getRetryCount());
        printRuleResults(resp);
    }

    @Test
    @Order(2)
    @DisplayName("Happy Path: Employee onboarding with all documents")
    void happyPath_employeeOnboarding() {
        var req = new WorkflowSubmitRequest();
        req.setIdempotencyKey(uniqueKey());
        req.setWorkflowType("employee-onboarding");
        req.setPayload(Map.of(
            "documentsSubmitted", true,
            "backgroundVerified", true,
            "department", "Engineering"
        ));
        WorkflowResponse resp = workflowService.submit(req);

        assertNotNull(resp);
        assertNotEquals(WorkflowStatus.REJECTED, resp.getStatus());
        System.out.println("=== ONBOARDING RESULT ===");
        System.out.println("Status: " + resp.getStatus());
    }

    // ── Rejection Scenarios ────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("Reject: Credit score below minimum threshold")
    void reject_lowCreditScore() {
        WorkflowResponse resp = workflowService.submit(
            loanRequest(uniqueKey(), 580, 800000, 1000000, 30));

        assertEquals(WorkflowStatus.REJECTED, resp.getStatus());
        assertTrue(resp.getDecisionReason().toLowerCase().contains("credit score") ||
                   resp.getDecisionReason().toLowerCase().contains("rule"),
            "Rejection reason should mention credit score or rule failure");

        System.out.println("=== REJECT (LOW CREDIT) ===");
        System.out.println("Reason: " + resp.getDecisionReason());
        printRuleResults(resp);
    }

    @Test
    @Order(4)
    @DisplayName("Reject: Applicant under minimum age")
    void reject_underAge() {
        WorkflowResponse resp = workflowService.submit(
            loanRequest(uniqueKey(), 700, 600000, 1000000, 19));

        assertEquals(WorkflowStatus.REJECTED, resp.getStatus());
        System.out.println("Reject (underage) reason: " + resp.getDecisionReason());
    }

    @Test
    @Order(5)
    @DisplayName("Reject: Loan-to-income ratio too high")
    void reject_highLoanToIncomeRatio() {
        // loanAmount / annualIncome = 6000000 / 500000 = 12x — exceeds limit of 5x
        WorkflowResponse resp = workflowService.submit(
            loanRequest(uniqueKey(), 700, 500000, 6000000, 30));

        assertEquals(WorkflowStatus.REJECTED, resp.getStatus());
        System.out.println("Reject (LTI ratio) reason: " + resp.getDecisionReason());
    }

    @Test
    @Order(6)
    @DisplayName("Manual Review: Income below threshold but other rules pass")
    void manualReview_lowIncome() {
        // creditScore >= 650 ✓, income < 300000 → MANUAL_REVIEW, age ≥ 21 ✓
        // loanAmount/income might also be high — but income rule triggers MANUAL_REVIEW first
        WorkflowResponse resp = workflowService.submit(
            loanRequest(uniqueKey(), 680, 200000, 500000, 30));

        // Could be MANUAL_REVIEW or REJECTED depending on LTI ratio
        assertTrue(
            resp.getStatus() == WorkflowStatus.MANUAL_REVIEW ||
            resp.getStatus() == WorkflowStatus.REJECTED,
            "Should be MANUAL_REVIEW or REJECTED for low income"
        );
        System.out.println("Low income result: " + resp.getStatus() + " — " + resp.getDecisionReason());
    }

    @Test
    @Order(7)
    @DisplayName("Reject: Employee missing background verification")
    void reject_employeeNoBackgroundCheck() {
        var req = new WorkflowSubmitRequest();
        req.setIdempotencyKey(uniqueKey());
        req.setWorkflowType("employee-onboarding");
        req.setPayload(Map.of(
            "documentsSubmitted", true,
            "backgroundVerified", false,
            "department", "Finance"
        ));
        WorkflowResponse resp = workflowService.submit(req);
        assertEquals(WorkflowStatus.REJECTED, resp.getStatus());
        System.out.println("Onboarding reject reason: " + resp.getDecisionReason());
    }

    // ── Idempotency ────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("Idempotency: Duplicate key should throw DuplicateRequestException")
    void duplicate_idempotencyKey_shouldThrow() {
        String key = uniqueKey();
        workflowService.submit(loanRequest(key, 700, 600000, 1000000, 30));

        DuplicateRequestException ex = assertThrows(DuplicateRequestException.class,
            () -> workflowService.submit(loanRequest(key, 700, 600000, 1000000, 30)));

        assertNotNull(ex.getExistingResponse(), "Exception should carry existing workflow response");
        assertEquals(key, ex.getExistingResponse().getIdempotencyKey());
        System.out.println("Duplicate correctly rejected: " + ex.getMessage());
    }

    @Test
    @Order(9)
    @DisplayName("Idempotency: Same key with different payload still returns existing workflow")
    void duplicate_differentPayload_returnsOriginal() {
        String key = uniqueKey();
        WorkflowResponse first = workflowService.submit(
            loanRequest(key, 700, 600000, 1000000, 30));

        DuplicateRequestException ex = assertThrows(DuplicateRequestException.class,
            () -> workflowService.submit(loanRequest(key, 800, 900000, 500000, 40)));

        assertEquals(first.getId(), ex.getExistingResponse().getId(),
            "Should return the original workflow, not a new one");
    }

    // ── Invalid Input ──────────────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("Invalid: Unknown workflow type should throw IllegalArgumentException")
    void invalidWorkflowType_shouldThrow() {
        var req = new WorkflowSubmitRequest();
        req.setIdempotencyKey(uniqueKey());
        req.setWorkflowType("unknown-type");
        req.setPayload(Map.of("foo", "bar"));

        assertThrows(IllegalArgumentException.class, () -> workflowService.submit(req));
    }

    // ── Audit & Explainability ─────────────────────────────────────────────────

    @Test
    @Order(11)
    @DisplayName("Audit: Full audit trail should be present with all stage transitions")
    void auditTrail_shouldBeComplete() {
        WorkflowResponse resp = workflowService.submit(
            loanRequest(uniqueKey(), 720, 900000, 2000000, 32));

        assertFalse(resp.getAuditLogs().isEmpty(), "Audit logs must not be empty");
        assertFalse(resp.getRuleResults().isEmpty(), "Rule results must not be empty");

        // Verify audit entries cover key stages
        var stages = resp.getAuditLogs().stream()
            .map(a -> a.getStage().name())
            .distinct()
            .toList();
        assertTrue(stages.contains("VALIDATION"), "Should have VALIDATION audit entry");
        assertTrue(stages.contains("RULE_EVALUATION"), "Should have RULE_EVALUATION audit entry");
        assertTrue(stages.contains("COMPLETED"), "Should have COMPLETED audit entry");

        System.out.println("=== FULL AUDIT TRAIL ===");
        resp.getAuditLogs().forEach(a ->
            System.out.printf("[%s] %s → %s | %s%n",
                a.getStage(), a.getStatusBefore(), a.getStatusAfter(), a.getMessage()));

        System.out.println("\n=== RULE EXPLANATIONS ===");
        printRuleResults(resp);
    }

    @Test
    @Order(12)
    @DisplayName("Rule results: Each rule should have explanation text")
    void ruleResults_shouldHaveExplanations() {
        WorkflowResponse resp = workflowService.submit(
            loanRequest(uniqueKey(), 700, 500000, 1000000, 28));

        resp.getRuleResults().forEach(r -> {
            assertNotNull(r.getExplanation(), "Rule explanation should not be null");
            assertFalse(r.getExplanation().isBlank(), "Rule explanation should not be blank");
            assertNotNull(r.getActualValue(), "Actual value should be recorded");
        });
    }

    // ── Lookup ─────────────────────────────────────────────────────────────────

    @Test
    @Order(13)
    @DisplayName("Lookup: Should retrieve workflow by ID")
    void getById_shouldReturnWorkflow() {
        WorkflowResponse created = workflowService.submit(
            loanRequest(uniqueKey(), 700, 600000, 1000000, 30));

        WorkflowResponse fetched = workflowService.getById(created.getId());
        assertEquals(created.getId(), fetched.getId());
        assertEquals(created.getIdempotencyKey(), fetched.getIdempotencyKey());
    }

    @Test
    @Order(14)
    @DisplayName("Lookup: Should retrieve workflow by idempotency key")
    void getByKey_shouldReturnWorkflow() {
        String key = uniqueKey();
        workflowService.submit(loanRequest(key, 700, 600000, 1000000, 30));

        WorkflowResponse fetched = workflowService.getByIdempotencyKey(key);
        assertEquals(key, fetched.getIdempotencyKey());
    }

    @Test
    @Order(15)
    @DisplayName("Lookup: Non-existent ID should throw WorkflowNotFoundException")
    void getById_nonExistent_shouldThrow() {
        assertThrows(com.workflow.exception.WorkflowNotFoundException.class,
            () -> workflowService.getById(999999L));
    }

    // ── Helper ─────────────────────────────────────────────────────────────────
    private void printRuleResults(WorkflowResponse resp) {
        System.out.println("Rule Results:");
        resp.getRuleResults().forEach(r ->
            System.out.printf("  [%s] %s — actual=%s — %s%n",
                r.isPassed() ? "PASS" : "FAIL",
                r.getRuleName(),
                r.getActualValue(),
                r.getExplanation()));
    }
}
