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

1. Confirm the reference page is a concrete API page.
- Prefer pages that describe request path, parameters, and error semantics.
- If page is only overview/index, ask for a concrete API detail page when strict assertions are required.

2. Build request payload.
- Always include `requirement`.
- Include `referenceUrl` when KB hit is uncertain.
- Include explicit expectations (`expectedHttpStatus`, `expectedErrorCode`, `expectedErrorDescription`) when provided by user.

3. Call local generation API.
- Save response body for traceability and later compile validation.

4. Validate response structure.
- Success body should include top-level fields:
  - `javaTestCode`
  - `citations`
  - `degraded`

5. Validate generated code safety.
- Reject code containing `TODO` or placeholder tokens.
- Confirm explicit expectations appear in assertions when explicitly provided.

6. Compile with Java 21.
- Extract class name from generated code.
- Save as `/tmp/<ClassName>.java`.
- Compile with JUnit API jars from local Maven cache.

## Failure Rules

- If `referenceUrl` is missing and KB has no concrete hit:
  - service returns `TESTCASE_REFERENCE_URL_REQUIRED` (no testcase code should be produced)
- If explicit expectations are not provided and context has no clear status/error semantics:
  - do not fabricate concrete status code, error code, or error description.

## Minimal Curl Example

```bash
curl -sS -o /tmp/testcase.out -w 'http_status=%{http_code}\n' -m 260 \
  -H 'Content-Type: application/json' \
  -X POST http://127.0.0.1:8080/api/testcase/generate \
  --data '{
    "requirement":"验证删除工作流接口在参数非法时返回400，并校验错误描述",
    "referenceUrl":"https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html",
    "expectedHttpStatus":400,
    "expectedErrorDescription":"示例错误描述"
  }'
```

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
