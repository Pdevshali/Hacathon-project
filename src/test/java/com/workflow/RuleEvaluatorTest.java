package com.workflow;

import com.workflow.config.WorkflowProperties;
import com.workflow.engine.rule.RuleEvaluator;
import com.workflow.model.entity.WorkflowRequest;
import com.workflow.model.enums.WorkflowStage;
import com.workflow.model.enums.WorkflowStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RuleEvaluatorTest {

    @Autowired
    private RuleEvaluator ruleEvaluator;

    @Autowired
    private com.workflow.repository.WorkflowRequestRepository requestRepo;

    private WorkflowRequest createRequest(String payload) {
        WorkflowRequest req = WorkflowRequest.builder()
            .idempotencyKey("rule-test-" + System.nanoTime())
            .workflowType("loan-approval")
            .status(WorkflowStatus.IN_PROGRESS)
            .currentStage(WorkflowStage.RULE_EVALUATION)
            .payload(payload)
            .retryCount(0)
            .build();
        return requestRepo.save(req);
    }

    @Test
    @DisplayName("All rules pass for excellent applicant")
    void allRulesPass_excellentApplicant() {
        WorkflowRequest req = createRequest(
            "{\"creditScore\":800,\"annualIncome\":1500000,\"loanAmount\":3000000,\"age\":40}");

        RuleEvaluator.RuleEvaluationOutcome outcome = ruleEvaluator.evaluate(req);

        assertTrue(outcome.allPassed(), "All rules should pass");
        assertNull(outcome.failAction());
    }

    @Test
    @DisplayName("Credit score rule fails below 650")
    void creditScoreRule_fails() {
        WorkflowRequest req = createRequest(
            "{\"creditScore\":600,\"annualIncome\":800000,\"loanAmount\":1000000,\"age\":30}");

        RuleEvaluator.RuleEvaluationOutcome outcome = ruleEvaluator.evaluate(req);

        assertFalse(outcome.allPassed());
        assertNotNull(outcome.failAction());
        assertTrue(outcome.reason().contains("Credit Score"));
    }

    @Test
    @DisplayName("Loan-to-income derived field is computed correctly")
    void derivedField_loanToIncomeRatio() {
        // 5000000 / 500000 = 10.0 — exceeds limit of 5.0 → REJECT
        WorkflowRequest req = createRequest(
            "{\"creditScore\":750,\"annualIncome\":500000,\"loanAmount\":5000000,\"age\":30}");

        RuleEvaluator.RuleEvaluationOutcome outcome = ruleEvaluator.evaluate(req);

        assertFalse(outcome.allPassed());
        assertTrue(outcome.reason().toLowerCase().contains("loan") ||
                   outcome.reason().toLowerCase().contains("ratio") ||
                   outcome.results().stream().anyMatch(r ->
                       r.getFieldEvaluated().equals("loanToIncomeRatio") && !r.isPassed()));
    }

    @Test
    @DisplayName("Rule results contain explanation text for every evaluated rule")
    void ruleResults_haveExplanations() {
        WorkflowRequest req = createRequest(
            "{\"creditScore\":700,\"annualIncome\":600000,\"loanAmount\":1000000,\"age\":28}");

        RuleEvaluator.RuleEvaluationOutcome outcome = ruleEvaluator.evaluate(req);

        outcome.results().forEach(r -> {
            assertNotNull(r.getExplanation());
            assertFalse(r.getExplanation().isBlank());
            assertNotNull(r.getActualValue());
            assertNotNull(r.getRuleId());
        });
    }

    @Test
    @DisplayName("REJECT rule short-circuits further evaluation")
    void rejectRule_shortCircuits() {
        // Credit score 500 → immediate REJECT, should stop at first rule
        WorkflowRequest req = createRequest(
            "{\"creditScore\":500,\"annualIncome\":800000,\"loanAmount\":1000000,\"age\":30}");

        RuleEvaluator.RuleEvaluationOutcome outcome = ruleEvaluator.evaluate(req);

        assertFalse(outcome.allPassed());
        // After a REJECT rule, remaining rules should not appear in results
        // (priority 1 = credit score = REJECT → breaks early)
        assertTrue(outcome.results().size() < 4,
            "Should short-circuit and not evaluate all 4 rules after a REJECT");
    }
}
