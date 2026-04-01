package com.workflow.hacathone_workflow.model;

public class WorkflowRequest {
    private String idempotencyKey;
    private String workflowType;
    private WorkflowPayload payload;

    public WorkflowRequest() {}

    public WorkflowRequest(String idempotencyKey, String workflowType, WorkflowPayload payload) {
        this.idempotencyKey = idempotencyKey;
        this.workflowType = workflowType;
        this.payload = payload;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getWorkflowType() {
        return workflowType;
    }

    public void setWorkflowType(String workflowType) {
        this.workflowType = workflowType;
    }

    public WorkflowPayload getPayload() {
        return payload;
    }

    public void setPayload(WorkflowPayload payload) {
        this.payload = payload;
    }
}
