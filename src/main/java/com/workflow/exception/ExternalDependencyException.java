package com.workflow.exception;

public class ExternalDependencyException extends RuntimeException {
    public ExternalDependencyException(String message) {
        super(message);
    }
}
