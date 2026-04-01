package com.workflow.model.entity;

import com.workflow.model.enums.WorkflowStage;
import com.workflow.model.enums.WorkflowStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workflow_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private String workflowType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private WorkflowStatus status;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private WorkflowStage currentStage;

    @Column(columnDefinition = "TEXT")
    private String payload;       // JSON-serialized input data

    @Column(columnDefinition = "TEXT")
    private String decisionReason;

    private int retryCount;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "workflowRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AuditLog> auditLogs = new ArrayList<>();

    @OneToMany(mappedBy = "workflowRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RuleEvaluationResult> ruleResults = new ArrayList<>();
}
