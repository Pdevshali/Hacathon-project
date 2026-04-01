package com.workflow.hacathone_workflow.controller;

import com.workflow.hacathone_workflow.model.WorkflowRequest;
import com.workflow.hacathone_workflow.service.WorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class WorkflowController {

    @Autowired
    private WorkflowService workflowService;

    @PostMapping("/workflows")
    public ResponseEntity<?> createWorkflow(@RequestBody WorkflowRequest request) {
        try {
            Object result = workflowService.processWorkflow(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error processing workflow: " + e.getMessage());
        }
    }
}
