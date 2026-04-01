# Configurable Workflow Decision Platform

A resilient, configurable workflow decision system built with **Java 17 + Spring Boot 3.2**.  
Supports multiple business workflows (loan approval, employee onboarding, etc.) via YAML configuration — no code changes required to update rules or thresholds.

---

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+

### Run
```bash
./mvnw spring-boot:run
```

The server starts at `http://localhost:8080`.  
H2 console (in-memory DB): `http://localhost:8080/h2-console`  
- JDBC URL: `jdbc:h2:mem:workflowdb`  
- Username: `sa`, Password: *(blank)*

### Run Tests
```bash
./mvnw test
```

---

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/workflows` | Submit a new workflow request |
| GET | `/api/v1/workflows` | List all workflow requests |
| GET | `/api/v1/workflows/{id}` | Get workflow by ID (with full audit trail) |
| GET | `/api/v1/workflows/by-key/{key}` | Lookup by idempotency key |
| GET | `/api/v1/workflows/by-status/{status}` | Filter by status |

---

## Example Requests

### Loan Approval — Approved
```bash
curl -X POST http://localhost:8080/api/v1/workflows \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "loan-001",
    "workflowType": "loan-approval",
    "payload": {
      "creditScore": 750,
      "annualIncome": 1200000,
      "loanAmount": 3000000,
      "age": 35
    }
  }'
```

### Loan Approval — Rejected (low credit score)
```bash
curl -X POST http://localhost:8080/api/v1/workflows \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "loan-002",
    "workflowType": "loan-approval",
    "payload": {
      "creditScore": 580,
      "annualIncome": 800000,
      "loanAmount": 1000000,
      "age": 28
    }
  }'
```

### Employee Onboarding
```bash
curl -X POST http://localhost:8080/api/v1/workflows \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "emp-001",
    "workflowType": "employee-onboarding",
    "payload": {
      "documentsSubmitted": true,
      "backgroundVerified": true,
      "department": "Engineering"
    }
  }'
```

### Idempotent Re-submission (returns 409 + existing workflow)
```bash
# Re-send any request with the same idempotencyKey
curl -X POST http://localhost:8080/api/v1/workflows \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "loan-001",
    "workflowType": "loan-approval",
    "payload": { "creditScore": 999 }
  }'
# → HTTP 409 Conflict with existing workflow in body
```

---

## Configuration (No Code Changes Required)

Rules and workflow configuration live in `src/main/resources/application.yml`.

```yaml
workflow:
  configs:
    loan-approval:
      name: "Loan Approval Workflow"
      rules:
        - id: CREDIT_SCORE_CHECK
          field: creditScore
          operator: GREATER_THAN_OR_EQUAL
          value: "650"          # ← Change this threshold
          priority: 1
          onFail: REJECT        # ← REJECT | MANUAL_REVIEW | CONTINUE
```

### Adding a new rule (zero code change)
```yaml
        - id: EMPLOYMENT_CHECK
          name: "Employment Status"
          field: employed
          operator: EQUALS
          value: "true"
          priority: 5
          onFail: MANUAL_REVIEW
```

### Adding a new workflow type (zero code change)
```yaml
    vendor-approval:
      name: "Vendor Approval Workflow"
      stages: ...
      rules:
        - id: GSTIN_CHECK
          field: gstinVerified
          operator: EQUALS
          value: "true"
          priority: 1
          onFail: REJECT
```

### Supported Rule Operators
| Operator | Usage |
|----------|-------|
| `EQUALS` | Exact match (case-insensitive) |
| `NOT_EQUALS` | Inverse match |
| `GREATER_THAN` | Numeric comparison |
| `GREATER_THAN_OR_EQUAL` | Numeric comparison |
| `LESS_THAN` | Numeric comparison |
| `LESS_THAN_OR_EQUAL` | Numeric comparison |
| `CONTAINS` | String contains |
| `NOT_EMPTY` | Field exists and is non-blank |

---

## Architecture

```
HTTP Request
    │
    ▼
WorkflowController
    │
    ▼
WorkflowService          ← Idempotency check, payload serialization, orchestration
    │
    ▼
WorkflowExecutor         ← Stage machine: VALIDATION → RULE_EVAL → EXTERNAL_CHECK → DECISION
    ├── RuleEvaluator          ← Loads rules from WorkflowProperties (YAML), evaluates with short-circuit
    ├── ExternalDependencyService  ← Simulated external call with configurable failure rate
    └── AuditService           ← Immutable audit log on every state transition
    │
    ▼
H2 / PostgreSQL (swap via application.yml)
```

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Rules in YAML, not DB | Zero code rewrite for rule changes; git-trackable config; ops team can change thresholds |
| Idempotency via unique DB constraint | Atomic — prevents race conditions even under concurrent requests |
| REJECT short-circuits rule evaluation | Mirrors real credit bureau behavior; avoids leaking partial pass/fail info |
| Derived fields computed pre-evaluation | `loanToIncomeRatio` from `loanAmount/annualIncome` — keeps payload simple for callers |
| Immutable audit logs | Every state transition is append-only; nothing is ever updated or deleted |
| Retry with re-entry point | External failures retry only the external check, not full pipeline, preserving rule results |
| Synchronous execution (with async hook) | Simpler for demo; `@Async` annotation + executor ready for queue-based swap |

---

## Workflow Stages

```
PENDING → VALIDATION → RULE_EVALUATION → EXTERNAL_CHECK → DECISION → COMPLETED
                                                 ↑                 ↓
                                              RETRYING          APPROVED
                                                              REJECTED
                                                           MANUAL_REVIEW
                                                               FAILED
```

---

## Scaling Considerations

| Layer | Current | Production Path |
|-------|---------|-----------------|
| Database | H2 in-memory | PostgreSQL / Aurora with connection pool |
| Execution | Synchronous in-request | Kafka/SQS consumer per workflow type |
| Idempotency | DB unique constraint | Redis distributed lock (TTL-based) |
| Retries | Thread.sleep() | Exponential backoff with jitter via Spring Retry |
| Rules | YAML file | DB-backed rule store with versioning and rollback |
| Audit | Local DB | Append-only event store (e.g., Kafka compacted topic) |
| API | Single instance | Horizontally scalable — stateless design |

---

## Test Coverage

| Test Class | Scenarios |
|------------|-----------|
| `WorkflowServiceTest` | Happy path, reject (credit/age/LTI), manual review, idempotency (same+different payload), invalid type, audit trail completeness, rule explanations, lookups |
| `RuleEvaluatorTest` | All rules pass, single rule fail, derived field, explanation presence, REJECT short-circuit |
| `WorkflowControllerTest` | 201 create, 409 duplicate, 400 missing fields, 400 unknown type, 200 getById, 404 not found, by-key, by-status |

---

## Decision Explanation Example

```json
{
  "id": 1,
  "idempotencyKey": "loan-001",
  "workflowType": "loan-approval",
  "status": "REJECTED",
  "decisionReason": "Rule evaluation failed: Credit Score Minimum: [FAILED] ...",
  "ruleResults": [
    {
      "ruleId": "CREDIT_SCORE_CHECK",
      "ruleName": "Credit Score Minimum",
      "fieldEvaluated": "creditScore",
      "expectedValue": "650",
      "actualValue": "580",
      "operator": "GREATER_THAN_OR_EQUAL",
      "passed": false,
      "failAction": "REJECT",
      "explanation": "[FAILED] Credit Score Minimum: field 'creditScore' = '580' GREATER_THAN_OR_EQUAL '650'"
    }
  ],
  "auditLogs": [
    { "stage": "VALIDATION",       "statusBefore": null,        "statusAfter": "PENDING",     "message": "Workflow submitted by user" },
    { "stage": "VALIDATION",       "statusBefore": "PENDING",   "statusAfter": "IN_PROGRESS", "message": "Validation stage started" },
    { "stage": "RULE_EVALUATION",  "statusBefore": "IN_PROGRESS","statusAfter": "IN_PROGRESS","message": "Rule evaluation started" },
    { "stage": "COMPLETED",        "statusBefore": "IN_PROGRESS","statusAfter": "REJECTED",   "message": "Workflow completed with status: REJECTED" }
  ]
}
```
