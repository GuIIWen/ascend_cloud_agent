---
name: huawei-testcase-generation
description: Generate Huawei Cloud API Java testcases from requirement plus referenceUrl by calling /api/testcase/generate, then validate response fields, placeholder safety, and Java 21 compilation.
---

# Huawei Testcase Generation

Use this skill when the user wants to generate Java testcase code from a testcase description and Huawei Cloud API docs.

## Trigger Conditions

- User asks to generate Java testcase code for Huawei Cloud APIs.
- User provides testcase requirement plus an API doc URL.
- User asks to validate generated testcase output end-to-end (API response + compile check).

## Input Contract

Request target: `POST /api/testcase/generate`

Required:
- `requirement` (string)

Optional:
- `referenceUrl` (string, must start with `http://` or `https://`)
- `expectedHttpStatus` (integer, valid range `100-599`)
- `expectedErrorCode` (string; blank should be treated as null)
- `expectedErrorDescription` (string; blank should be treated as null)

## Auth And Runtime Parameters

Generated tests should read runtime values from env vars or system properties:

- Auth token:
  - env: `HUAWEICLOUD_AUTH_TOKEN`
  - sysprop: `-Dhwcloud.auth.token=...`
- Project ID:
  - env: `HUAWEICLOUD_PROJECT_ID`
  - sysprop: `-Dhwcloud.project.id=...`
- Base URL:
  - env: `HUAWEICLOUD_BASE_URL`
  - sysprop: `-Dhwcloud.base.url=...`

## Standard Flow

1. Requirement refinement (测试用例描述优化).
- Produce a structured description (service, resource, operation, preconditions, inputs, expected outcomes).
- If expected status/error semantics are not explicit and cannot be inferred from context, require the user to supply `referenceUrl` or explicit expectations.

2. Retrieval from KB.
- Query KB using the refined description, not the raw requirement.
- Treat a hit as **concrete** only when `apiId` exists and at least one of `httpMethod/endpoint/requestBody/responseBody/className/methodName/signature` is present.
- Treat missing/weak hits as KB miss to avoid API drift.

3. Reference URL fallback.
- If KB miss or weak hit, require `referenceUrl`.
- If `referenceUrl` is an overview/index page, ask for a concrete API detail page.
- Fetch and truncate page content as temporary context (do not persist it).

4. Build request payload.
- Always include `requirement` (use the refined description content if required by your prompt).
- Include `referenceUrl` only when needed (KB miss/weak hit).
- Include explicit expectations (`expectedHttpStatus`, `expectedErrorCode`, `expectedErrorDescription`) when provided by user.

5. Call local generation API.
- Save response body for traceability and later compile validation.

6. Validate response structure.
- Success body should include top-level fields:
  - `javaTestCode`
  - `citations`
  - `degraded`

7. Validate generated code safety and expectations.
- Reject code containing `TODO` or placeholder tokens.
- Confirm explicit expectations appear in assertions when explicitly provided.

8. Compile with Java 21.
- Extract class name from generated code.
- Save as `/tmp/<ClassName>.java`.
- Compile with JUnit API jars from local Maven cache.

## Failure Rules

- If `referenceUrl` is missing and KB has no concrete hit:
  - service returns `TESTCASE_REFERENCE_URL_REQUIRED` (no testcase code should be produced)
- If KB hit is weak (missing concrete API metadata), treat it as a miss and require `referenceUrl`.
- If explicit expectations are not provided and context has no clear status/error semantics:
  - do not fabricate concrete status code, error code, or error description.

## Minimal Curl Example (Example Only, Not a Default API)

```bash
curl -sS -o /tmp/testcase.out -w 'http_status=%{http_code}\n' -m 260 \
  -H 'Content-Type: application/json' \
  -X POST http://127.0.0.1:8080/api/testcase/generate \
  --data '{
    "requirement":"示例：验证某 API 在参数非法时返回错误并校验错误描述",
    "referenceUrl":"https://support.huaweicloud.com/api-modelarts/DetachDevServerVolume.html"
  }'
```

## Example (Not the Default Path)

Example API documentation (for illustration only):
- `https://support.huaweicloud.com/api-modelarts/DetachDevServerVolume.html`

If you use this example, make sure the real scenario matches the API semantics.
Do not treat this URL as the default when the requirement is unrelated.

## Minimal Compile Validation Example

```bash
python3 - <<'PY'
import json, re
from pathlib import Path
body = json.loads(Path('/tmp/testcase.out').read_text())
code = body['javaTestCode']
assert 'TODO' not in code
assert 'placeholder' not in code.lower()
name = re.search(r'public\s+class\s+(\w+)', code).group(1)
Path(f'/tmp/{name}.java').write_text(code)
print(name)
PY

CLASS_NAME="$(python3 - <<'PY'
import json, re
from pathlib import Path
code = json.loads(Path('/tmp/testcase.out').read_text())['javaTestCode']
print(re.search(r'public\s+class\s+(\w+)', code).group(1))
PY
)"

JUNIT_API="$HOME/.m2/repository/org/junit/jupiter/junit-jupiter-api/5.10.1/junit-jupiter-api-5.10.1.jar"
JUNIT_COMMONS="$HOME/.m2/repository/org/junit/platform/junit-platform-commons/1.10.1/junit-platform-commons-1.10.1.jar"
OPENTEST4J="$HOME/.m2/repository/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar"
APIGUARDIAN="$HOME/.m2/repository/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar"

/usr/lib/jvm/java-21-openjdk-amd64/bin/javac \
  -cp "$JUNIT_API:$JUNIT_COMMONS:$OPENTEST4J:$APIGUARDIAN" \
  "/tmp/${CLASS_NAME}.java"
```

## Acceptance Checklist

- Request payload matches latest contract.
- Response includes `javaTestCode`, `citations`, `degraded`.
- Generated code contains no placeholder/TODO.
- If expectations were provided, assertions reflect them.
- Java 21 compilation succeeds.
