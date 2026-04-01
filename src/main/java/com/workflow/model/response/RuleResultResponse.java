package com.workflow.model.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class RuleResultResponse {
    private String ruleId;
    private String ruleName;
    private String fieldEvaluated;
    private String expectedValue;
    private String actualValue;
    private String operator;
    private boolean passed;
    private String failAction;
    private String explanation;
    private LocalDateTime evaluatedAt;
}
