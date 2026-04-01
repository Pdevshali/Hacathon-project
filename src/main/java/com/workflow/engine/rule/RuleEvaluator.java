package com.workflow.engine.rule;

import com.workflow.config.WorkflowProperties;
import com.workflow.model.entity.RuleEvaluationResult;
import com.workflow.model.entity.WorkflowRequest;
import com.workflow.model.enums.RuleFailAction;
import com.workflow.model.enums.RuleOperator;
import com.workflow.repository.RuleEvaluationResultRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEvaluator {

    private final WorkflowProperties properties;
    private final RuleEvaluationResultRepository ruleResultRepo;
    private final ObjectMapper objectMapper;

    public RuleEvaluationOutcome evaluate(WorkflowRequest request) {
        WorkflowProperties.WorkflowConfig config =
            properties.getConfigs().get(request.getWorkflowType());

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(request.getPayload(), new TypeReference<>() {});
        } catch (Exception e) {
            return new RuleEvaluationOutcome(false, RuleFailAction.REJECT,
                "Failed to parse payload: " + e.getMessage(), List.of());
        }

        // Compute derived fields before rule evaluation
        enrichPayload(payload);

        List<RuleEvaluationResult> results = new ArrayList<>();
        RuleFailAction worstOutcome = null;
        List<String> failReasons = new ArrayList<>();

        // Evaluate rules sorted by priority
        List<WorkflowProperties.RuleConfig> sortedRules = config.getRules().stream()
            .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
            .toList();

        for (WorkflowProperties.RuleConfig rule : sortedRules) {
            Object rawValue = payload.get(rule.getField());
            String actualValue = rawValue != null ? rawValue.toString() : "null";
            boolean passed = evaluateRule(actualValue, rule.getOperator(), rule.getValue());

            String explanation = buildExplanation(rule, actualValue, passed);
            log.debug("Rule [{}]: field={}, actual={}, expected={}, passed={}",
                rule.getId(), rule.getField(), actualValue, rule.getValue(), passed);

            RuleEvaluationResult result = RuleEvaluationResult.builder()
                .workflowRequest(request)
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .fieldEvaluated(rule.getField())
                .expectedValue(rule.getValue())
                .actualValue(actualValue)
                .operator(rule.getOperator().name())
                .passed(passed)
                .failAction(passed ? "N/A" : rule.getOnFail().name())
                .explanation(explanation)
                .build();

            results.add(ruleResultRepo.save(result));

            if (!passed) {
                failReasons.add(rule.getName() + ": " + explanation);
                // Track the worst outcome (REJECT > MANUAL_REVIEW > CONTINUE)
                if (worstOutcome == null || rule.getOnFail() == RuleFailAction.REJECT) {
                    worstOutcome = rule.getOnFail();
                }
                // REJECT short-circuits immediately
                if (rule.getOnFail() == RuleFailAction.REJECT) {
                    break;
                }
            }
        }

        if (worstOutcome == null) {
            return new RuleEvaluationOutcome(true, null, "All rules passed", results);
        }
        return new RuleEvaluationOutcome(false, worstOutcome,
            String.join("; ", failReasons), results);
    }

    private void enrichPayload(Map<String, Object> payload) {
        // Compute loan-to-income ratio as a derived field
        try {
            if (payload.containsKey("loanAmount") && payload.containsKey("annualIncome")) {
                double loan = Double.parseDouble(payload.get("loanAmount").toString());
                double income = Double.parseDouble(payload.get("annualIncome").toString());
                if (income > 0) {
                    double ratio = Math.round((loan / income) * 100.0) / 100.0;
                    payload.put("loanToIncomeRatio", ratio);
                    log.debug("Derived loanToIncomeRatio = {}", ratio);
                }
            }
        } catch (NumberFormatException ignored) {}
    }

    private boolean evaluateRule(String actual, RuleOperator operator, String expected) {
        try {
            return switch (operator) {
                case EQUALS -> actual.equalsIgnoreCase(expected);
                case NOT_EQUALS -> !actual.equalsIgnoreCase(expected);
                case GREATER_THAN -> Double.parseDouble(actual) > Double.parseDouble(expected);
                case GREATER_THAN_OR_EQUAL -> Double.parseDouble(actual) >= Double.parseDouble(expected);
                case LESS_THAN -> Double.parseDouble(actual) < Double.parseDouble(expected);
                case LESS_THAN_OR_EQUAL -> Double.parseDouble(actual) <= Double.parseDouble(expected);
                case CONTAINS -> actual.toLowerCase().contains(expected.toLowerCase());
                case NOT_EMPTY -> actual != null && !actual.isBlank() && !actual.equals("null");
            };
        } catch (NumberFormatException e) {
            log.warn("Cannot compare non-numeric values '{}' with operator {}", actual, operator);
            return false;
        }
    }

    private String buildExplanation(WorkflowProperties.RuleConfig rule,
                                     String actual, boolean passed) {
        String status = passed ? "PASSED" : "FAILED";
        return String.format("[%s] %s: field '%s' = '%s' %s '%s'",
            status, rule.getName(), rule.getField(),
            actual, rule.getOperator().name(), rule.getValue());
    }

    public record RuleEvaluationOutcome(
        boolean allPassed,
        RuleFailAction failAction,
        String reason,
        List<RuleEvaluationResult> results
    ) {}
}
