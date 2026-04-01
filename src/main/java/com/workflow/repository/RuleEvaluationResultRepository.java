package com.workflow.repository;

import com.workflow.model.entity.RuleEvaluationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleEvaluationResultRepository extends JpaRepository<RuleEvaluationResult, Long> {
    List<RuleEvaluationResult> findByWorkflowRequestIdOrderByEvaluatedAtAsc(Long workflowRequestId);
}
