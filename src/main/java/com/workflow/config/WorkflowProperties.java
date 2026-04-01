package com.workflow.config;

import com.workflow.model.enums.RuleFailAction;
import com.workflow.model.enums.RuleOperator;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "workflow")
@Data
public class WorkflowProperties {

    private Map<String, WorkflowConfig> configs;
    private ExternalDependencyConfig externalDependency;

    @Data
    public static class WorkflowConfig {
        private String name;
        private String description;
        private List<StageConfig> stages;
        private List<RuleConfig> rules;
        private int maxRetries = 3;
        private long retryDelayMs = 1000;
    }

    @Data
    public static class StageConfig {
        private String name;
        private int order;
        private boolean required;
    }

    @Data
    public static class RuleConfig {
        private String id;
        private String name;
        private String field;
        private RuleOperator operator;
        private String value;
        private int priority;
        private RuleFailAction onFail;
    }

    @Data
    public static class ExternalDependencyConfig {
        private String url;
        private int timeoutMs = 3000;
        private double simulateFailureRate = 0.2;
    }
}
