package com.workflow.model.entity;

import com.workflow.model.enums.WorkflowStage;
import com.workflow.model.enums.WorkflowStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_request_id", nullable = false)
    private WorkflowRequest workflowRequest;

    @Enumerated(EnumType.STRING)
    private WorkflowStage stage;

    @Enumerated(EnumType.STRING)
    private WorkflowStatus statusBefore;

    @Enumerated(EnumType.STRING)
    private WorkflowStatus statusAfter;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String details;

    private String actor; // system or user

    @CreationTimestamp
    private LocalDateTime timestamp;
}
