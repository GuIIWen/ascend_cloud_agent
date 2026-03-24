# Testcase Generation Batch 3 (Current Version Design, V3)

Status: **Execution design** (current version, implementable constraints)

This document is the *current* Batch 3 design baseline for: **testcase requirement (+ optional referenceUrl) -> Java testcase code**.
It is intentionally constrained and must not drift into "收编" (planner/workflow/orchestrator or auto-execution).

Related hard-rule baseline:
- `docs/TESTCASE_GENERATION_MVP_CURRENT.md` (P10 approved hard rules; if any conflict, that file wins)

## 1. Background And Goal

Batch 3 goal:
- Input: a natural language testcase requirement (free text).
- Optional input: a `referenceUrl` (a web page that contains relevant API docs).
- Output: **Java testcase code** (a single-file Java test class as a string).

Key rules:
- KB hit: do **RAG** then use **custom LLM from configuration** to generate Java test code.
- KB miss + `referenceUrl` present: fetch/crawl the URL, extract temporary context, then use the **same custom LLM** to generate code.
- KB miss + `referenceUrl` absent: return error, **do not generate code**.

## 2. Non-Goals (Hard)

- No planner/workflow/orchestrator and no multi-step execution framework.
- No auto-running tests.
- No writing files to the repo (no `src/test/java/**` writes from the service).
- No PR creation.
- No new storage or middleware as a prerequisite.
- No new external API routes besides `POST /api/testcase/generate`.

## 3. User Entry And API Contract

Endpoint:
- `POST /api/testcase/generate`

Request JSON:
```json
{
  "requirement": "Given a valid token, create a workflow and verify it appears in list",
  "referenceUrl": "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html"
}
```

Response JSON (HTTP 200):
```json
{
  "javaTestCode": "/* full Java test code */",
  "citations": [
    {
      "type": "knowledge-base",
      "apiId": "huawei-modelarts-listWorkflows",
      "source": "https://support.huaweicloud.com/api-modelarts/ListWorkflows.html"
    },
    {
      "type": "reference-url",
      "source": "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html"
    }
  ]
}
```

Notes:
- `citations` is required for traceability (KB RAG sources and/or referenceUrl).
- "KB hit" is defined as: at least one retrieved item can be resolved to a concrete API metadata record (e.g. `apiId` exists and metadata lookup succeeds). A vector store returning text segments alone is not a KB hit.

## 4. Generation Chain (Single Request, No Orchestration)

All LLM calls must use the **custom LLM** configured via `knowledge-base.llm.*`.

1. Requirement refinement (LLM call #1)
   - Input: `requirement` (+ optional short summary from `referenceUrl` if the URL is provided and fetch succeeds).
   - Output: a structured, generation-friendly testcase description (stable schema, strict format).
   - Purpose: make retrieval query stable and make code generation deterministic.

2. Context acquisition (retrieval)
   - Query KB using the refined description.
   - If KB hit:
     - Build RAG context from top matches (API metadata + source links).
   - If KB miss and `referenceUrl` is present:
     - Fetch/crawl and extract content from `referenceUrl` as **temporary** context for this request.
     - Do not persist it as a new prerequisite (no new storage).
   - If KB miss and `referenceUrl` is absent:
     - Return the hard error `TESTCASE_REFERENCE_URL_REQUIRED` (no generation).

3. Java testcase code generation (LLM call #2)
   - Input: refined testcase description + context (KB RAG context or referenceUrl extracted content).
   - Output: a compilable Java testcase class (JUnit 5 baseline).
   - Guardrails:
     - Do not output placeholders like `TODO` or "skeleton only" when context is insufficient.
     - If neither KB context nor referenceUrl context exists, must error (Step 2 rule).

## 5. Error Semantics

Transport/framework errors:
- Invalid JSON/body types: `400/415` with structured JSON error payload.

Domain errors (hard rules):
- KB miss + no `referenceUrl`: HTTP `400` with:
  - `error.code=TESTCASE_REFERENCE_URL_REQUIRED`
  - `error.message` must instruct the user to provide `referenceUrl`
  - Response must not contain `javaTestCode`

Suggested error payload shape:
```json
{
  "error": {
    "code": "TESTCASE_REFERENCE_URL_REQUIRED",
    "message": "No related API found in knowledge base. Please provide referenceUrl to generate Java testcase code.",
    "timestamp": "2026-03-24T10:00:00+08:00"
  }
}
```

Degrade rules (allowed, but must remain safe):
- Reference URL fetch failure: may fall back to KB-only generation *only if* KB hit exists; otherwise it must still error (no code).
- KB retrieval failure should fail closed: do not generate code without a valid context source.

## 6. Configuration Dependencies (Custom LLM)

Both "requirement refinement" and "code generation" must call the same configurable custom LLM channel:
- `knowledge-base.llm.provider=custom`
- `knowledge-base.llm.api-url`
- `knowledge-base.llm.api-key`
- `knowledge-base.llm.model`
- Optional: `knowledge-base.llm.temperature`, `knowledge-base.llm.max-tokens`, `knowledge-base.llm.timeout-seconds`

Retrieval dependencies (existing KB stack):
- `knowledge-base.embedding.*`
- `knowledge-base.vector-store.*`

Batch 3 must not introduce a new config tree unless explicitly approved by P10.

## 7. Acceptance Criteria (Batch 3)

- `POST /api/testcase/generate` returns Java testcase code when:
  - KB hit exists, regardless of `referenceUrl`.
  - KB miss but `referenceUrl` is provided and fetch succeeds (uses temporary context).
- The endpoint returns an error and generates **no code** when:
  - KB miss and `referenceUrl` is absent.
- All generation steps use the configured custom LLM (no hardcoded vendor/model in code path).
- No new external API routes besides `/api/testcase/generate`.
- No new storage/middleware introduced.
- No workflow/planner/executor/orchestrator path introduced.
- Response includes `citations` for provenance.

## 8. Relationship With Historical Docs

The following docs are **goal design / larger scope** and must not be treated as Batch 3 execution baseline:
- `docs/DESIGN.md`
- `docs/API_TEST_GENERATOR.md`
- `docs/USE_CASE_OPTIMIZER.md`

Batch 3 execution hard rules:
- `docs/TESTCASE_GENERATION_MVP_CURRENT.md` remains the hard-rule baseline.

