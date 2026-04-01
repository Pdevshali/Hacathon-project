package com.workflow.controller;

import com.workflow.model.request.WorkflowSubmitRequest;
import com.workflow.model.response.WorkflowResponse;
import com.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    /**
     * POST /api/v1/workflows
     * Submit a new workflow request. Idempotent via idempotencyKey.
     */
    @PostMapping
    public ResponseEntity<WorkflowResponse> submit(
            @Valid @RequestBody WorkflowSubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(workflowService.submit(request));
    }

    /**
     * GET /api/v1/workflows
     * List all workflow requests.
     */
    @GetMapping
    public ResponseEntity<List<WorkflowResponse>> getAll() {
        return ResponseEntity.ok(workflowService.getAll());
    }

    /**
     * GET /api/v1/workflows/{id}
     * Get a workflow by its database ID with full audit trail and rule results.
     */
    @GetMapping("/{id}")
    public ResponseEntity<WorkflowResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(workflowService.getById(id));
    }

    /**
     * GET /api/v1/workflows/by-key/{key}
     * Lookup a workflow by its idempotency key.
     */
    @GetMapping("/by-key/{key}")
    public ResponseEntity<WorkflowResponse> getByKey(@PathVariable String key) {
        return ResponseEntity.ok(workflowService.getByIdempotencyKey(key));
    }

    /**
     * GET /api/v1/workflows/by-status/{status}
     * Filter workflows by status (PENDING, IN_PROGRESS, APPROVED, REJECTED, etc.)
     */
    @GetMapping("/by-status/{status}")
    public ResponseEntity<List<WorkflowResponse>> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(workflowService.getByStatus(status));
    }
}
