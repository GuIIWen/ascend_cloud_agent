# Testcase Generation MVP (Batch 3 Hard-Rule Summary / Historical Closure)

Status: **Historical summary** (not the active execution baseline)

This document captures the previously agreed Batch 3 hard rules as a compact summary.
It is kept for historical closure and quick reference, but it is **not** the active execution baseline.

## Background And Goal

Goal:
- Input: a natural language testcase requirement.
- Optional input: a `referenceUrl`.
- Output: **Java testcase code** (single-file string).

We reuse the existing knowledge-base capabilities (search + Huawei Cloud crawling + runtime contract) and add the smallest missing generation capability.

## Non-Goals (Hard)

- No planner/workflow/orchestrator, no multi-step task execution framework.
- No auto-running tests, no writing files to the repo, no PR creation.
- No new storage or middleware as a prerequisite.
- No "skeleton output" or "TODO placeholder code" when context is insufficient.

## User Entry And API Contract

Endpoint:
- `POST /api/testcase/generate`

Request JSON:
```json
{
  "requirement": "Given a valid token, create a workflow and verify it appears in list",
  "referenceUrl": "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html"
}
```

Success response (HTTP 200):
```json
{
  "javaTestCode": "/* full Java test code */",
  "citations": [
    {
      "type": "knowledge-base",
      "apiId": "huawei-listworkflows-123",
      "source": "https://support.huaweicloud.com/api-modelarts/ListWorkflows.html"
    }
  ]
}
```

Error response (HTTP 4xx, JSON body):
- Transport/framework errors may use `400/415` with a structured JSON error body.
- Hard rule: **if KB does not hit any API AND `referenceUrl` is absent, the service MUST return an error and MUST NOT generate any code.**

Example (KB miss + no URL, HTTP 400):
```json
{
  "error": {
    "code": "TESTCASE_REFERENCE_URL_REQUIRED",
    "message": "No related API found in knowledge base. Please provide referenceUrl to generate Java testcase code.",
    "timestamp": "2026-03-24T10:00:00+08:00"
  }
}
```

Notes:
- "KB hit" is defined as: at least one retrieved item can be resolved to a concrete API metadata record (for example, `apiId` is present and metadata lookup succeeds), not merely "vector returned some text segments".
- The endpoint must not introduce any new public routes besides `/api/testcase/generate`.

## Minimal Generation Chain (Single Request, No Orchestration)

Both Step 1 and Step 3 must be implemented by the **custom LLM** wired from configuration.

1. Requirement refinement (LLM call #1):
   - Input: `requirement` (+ optional `referenceUrl` content summary if available).
   - Output: a structured, generation-friendly testcase description (fixed schema, stable prompt).

2. Context acquisition (retrieval kept):
   - Query KB with the refined description.
   - If KB hit:
     - Build RAG context from top matches (API metadata + source links).
   - If KB miss and `referenceUrl` is provided:
     - Crawl and extract content from `referenceUrl` as temporary context for this request.
     - Do not persist it as a new prerequisite (no new storage).
   - If KB miss and `referenceUrl` is absent:
     - Return the hard error above (no generation).

3. Java testcase code generation (LLM call #2):
   - Input: refined testcase description + context (KB RAG or referenceUrl content).
   - Output: a compilable Java testcase class (JUnit 5 baseline is acceptable for MVP).

## Configuration Dependencies (Custom LLM)

The two LLM steps reuse the existing "custom LLM" channel:
- `knowledge-base.llm.provider=custom`
- `knowledge-base.llm.api-url`
- `knowledge-base.llm.api-key`
- `knowledge-base.llm.model`

Retrieval dependencies (already in place):
- `knowledge-base.embedding.*`
- `knowledge-base.vector-store.*`

MVP does not require adding a new config tree unless P10 approves it later. Prompt templates may live in code for Batch 3.

## Acceptance Criteria (Batch 3 MVP)

- The endpoint returns Java testcase code when:
  - KB hit exists, regardless of `referenceUrl`.
  - KB miss but `referenceUrl` is provided (uses temporary context).
- The endpoint returns an error and generates **no code** when:
  - KB miss and `referenceUrl` is absent.
- No new storage/middleware introduced.
- No workflow/planner/executor/orchestrator path introduced.
- No new external API routes besides `/api/testcase/generate`.
- Response includes `citations` so the user can trace the provenance of generated code context.

## Relationship With Historical Docs

These documents remain as **goal design / larger scope** and are not the execution baseline for Batch 3:
- `docs/DESIGN.md`
- `docs/API_TEST_GENERATOR.md`
- `docs/USE_CASE_OPTIMIZER.md`

For Batch 3 execution and acceptance, use:
- `docs/TESTCASE_GENERATION_V3_CURRENT.md`
- latest Batch 3 decisions in `meeting.md`
