package com.workflow.exception;

import com.workflow.model.response.WorkflowResponse;
import lombok.Getter;

@Getter
public class DuplicateRequestException extends RuntimeException {
    private final WorkflowResponse existingResponse;

    public DuplicateRequestException(String message, WorkflowResponse existingResponse) {
        super(message);
        this.existingResponse = existingResponse;
    }
}
