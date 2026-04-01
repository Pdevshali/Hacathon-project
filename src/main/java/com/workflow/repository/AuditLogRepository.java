package com.workflow.repository;

import com.workflow.model.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByWorkflowRequestIdOrderByTimestampAsc(Long workflowRequestId);
}
