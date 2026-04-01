package com.workflow.repository;

import com.workflow.model.entity.WorkflowRequest;
import com.workflow.model.enums.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowRequestRepository extends JpaRepository<WorkflowRequest, Long> {

    Optional<WorkflowRequest> findByIdempotencyKey(String idempotencyKey);

    List<WorkflowRequest> findByWorkflowType(String workflowType);

    List<WorkflowRequest> findByStatus(WorkflowStatus status);

    @Query("SELECT w FROM WorkflowRequest w WHERE w.status = 'RETRYING' AND w.retryCount < :maxRetries")
    List<WorkflowRequest> findRetryableRequests(int maxRetries);
}
