# Configuration Model (Zero-Code Workflow Engine)

The core philosophy of this platform is **zero-code configuration**. Analysts, product managers, and operation teams should be able to introduce new business workflows or modify thresholds instantly without requiring a code compilation step.

We accomplish this through the `application.yml` file, mapping human-readable workflows, stages, and execution rules directly into the Java engine structure via Spring Boot Configuration Properties.

## Overview of the Model

A `workflow` defines a collection of rules triggered on a JSON `payload`. A rule evaluates a specifically declared `field` against a threshold `value` using a well-defined `operator`.

### Structure of `application.yml`

```yaml
workflow:
  configs:
    <workflow_type_id>:                  # The unique ID used in the POST API payload
      name: "<Human Readable Name>"      # Display Name
      rules:
        - id: "<UNIQUE_RULE_ID>"         # To track Audit Logs and Responses
          name: "<Human Readable Name>"  # Used in the "explanations" block
          field: "<payload_key>"         # JSON path mapped directly to payload (e.g. creditScore)
          operator: <OPERATOR_ENUM>      # Math / String evaluation
          value: "<threshold_value>"     # Comparison base
          priority: <Integer>            # Defines the order of evaluation (Lower = first)
          onFail: <ACTION_ENUM>          # Strategy upon failed rule match
```

---

## 1. Modifying Thresholds & Parameters (Example: Loan Approval)

If risk management decides to increase minimum credit scores across operations, you update a single line in the configuration file.

```yaml
workflow:
  configs:
    loan-approval:
      name: "Loan Approval Workflow"
      rules:
        - id: CREDIT_SCORE_CHECK
          name: "Credit Score Minimum"
          field: creditScore
          operator: GREATER_THAN_OR_EQUAL
          value: "700"          # <- MODIFIED FROM 650
          priority: 1
          onFail: REJECT
```

**Restart the engine**, and the new behavior propagates globally to all incoming API calls immediately.

---

## 2. Dynamic Workflow Creation (Example: Vendor Approval)

Creating an entirely new system behavior does not require engineering resources to build new classes. To add a "Vendor Approval Workflow", define the entry under `configs`:

```yaml
    vendor-approval:
      name: "Vendor Approval Process"
      rules:
        - id: GSTIN_CHECK
          name: "GSTIN Must be Present"
          field: gstin
          operator: NOT_EMPTY
          priority: 1
          onFail: REJECT
          
        - id: ANNUAL_REVENUE_THRESHOLD
          name: "Minimum Annual Revenue Check"
          field: annualRevenue
          operator: GREATER_THAN
          value: "500000"
          priority: 2
          onFail: MANUAL_REVIEW
```

When calling `POST /api/v1/workflows` with `"workflowType": "vendor-approval"`, the engine instantiates this structure and evaluates the rules, bypassing any `loan-approval` rules.

---

## 3. Operations Dictionary

The `RuleOperator` enum natively supports the following operations for evaluating payload values against configuration baselines:

| Operator | Type | Description / Usage Example |
|----------|------|-----------------------------|
| `EQUALS` | String/Boolean/Num | `field: employed` `EQUALS` `value: "true"` |
| `NOT_EQUALS` | String/Boolean/Num | `field: status` `NOT_EQUALS` `value: "banned"` |
| `GREATER_THAN` | Numeric | `field: age` `GREATER_THAN` `value: "18"` |
| `LESS_THAN` | Numeric | `field: loanAmount` `LESS_THAN` `value: "500000"` |
| `GREATER_THAN_OR_EQUAL`| Numeric | `field: age` `>=` `value: "21"` |
| `LESS_THAN_OR_EQUAL` | Numeric | `field: loanAmount` `<=` `value: "500000"` |
| `CONTAINS` | String | `field: email` `CONTAINS` `value: "@company.com"` |
| `NOT_EMPTY` | N/A | Exists and is populated |

---

## 4. Failure Strategies & Branches

Workflows shouldn't always definitively block a user. Sometimes, a failure just means the workflow requires manual intervention. The platform implements the `onFail` configuration behavior model on each individual rule.

| `onFail` State | Executor Response |
|----------------|-------------------|
| `REJECT`       | Instant short-circuit. Stops evaluating other rules. Sets Workflow state to `REJECTED`. Adds a hard-stop explanation to the API response and audit log. |
| `MANUAL_REVIEW`| Defers the final decision. Continues evaluating other rules to provide maximum context. Sets final state to `MANUAL_REVIEW`. |
| `CONTINUE`     | Evaluates rule metrics and publishes failure logs to auditing, but never alters the state away from `APPROVED`. Generally used for A/B testing or silent background tracking. |
