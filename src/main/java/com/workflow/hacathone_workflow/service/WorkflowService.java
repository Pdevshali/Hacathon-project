package com.workflow.hacathone_workflow.service;

import com.workflow.hacathone_workflow.model.WorkflowRequest;
import com.workflow.hacathone_workflow.model.WorkflowPayload;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class WorkflowService {

    public Object processWorkflow(WorkflowRequest request) {
        if ("loan-approval".equals(request.getWorkflowType())) {
            return processLoanApproval(request.getPayload());
        } else {
            throw new IllegalArgumentException("Unsupported workflow type: " + request.getWorkflowType());
        }
    }

    private Map<String, Object> processLoanApproval(WorkflowPayload payload) {
        Map<String, Object> result = new HashMap<>();
        
        // Simple loan approval logic
        boolean approved = true;
        String reason = "";
        
        if (payload.getCreditScore() < 650) {
            approved = false;
            reason = "Credit score too low";
        } else if (payload.getAge() < 21 || payload.getAge() > 65) {
            approved = false;
            reason = "Age not within acceptable range";
        } else if (payload.getLoanAmount() > payload.getAnnualIncome() * 5) {
            approved = false;
            reason = "Loan amount exceeds 5x annual income";
        }
        
        result.put("idempotencyKey", "loan-001");
        result.put("status", approved ? "APPROVED" : "REJECTED");
        result.put("reason", reason);
        result.put("processedAt", java.time.LocalDateTime.now().toString());
        
        return result;
    }
}
