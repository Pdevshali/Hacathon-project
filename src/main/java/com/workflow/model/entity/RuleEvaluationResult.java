package com.workflow.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "rule_evaluation_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleEvaluationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_request_id", nullable = false)
    private WorkflowRequest workflowRequest;

    private String ruleId;
    private String ruleName;
    private String fieldEvaluated;
    private String expectedValue;
    private String actualValue;
    private String operator;
    private boolean passed;
    private String failAction;
    private String explanation;

    @CreationTimestamp
    private LocalDateTime evaluatedAt;
}
