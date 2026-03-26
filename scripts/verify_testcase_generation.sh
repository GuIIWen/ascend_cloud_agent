#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  bash scripts/verify_testcase_generation.sh \
    --requirement "..." \
    [--reference-url "https://..."] \
    [--expected-http-status 400] \
    [--expected-error-code ModelArts.7000] \
    [--expected-error-description "..."] \
    [--api http://127.0.0.1:8080/api/testcase/generate]

What it verifies:
  1. Calls /api/testcase/generate
  2. Checks response fields: javaTestCode / citations / degraded
  3. Rejects TODO / placeholder tokens
  4. Requires exactly one public class declaration
  5. Compiles the generated code with Java 21 + JUnit 5 jars
  6. If explicit expectations are provided, checks they appear in generated code
EOF
}

REQUIREMENT=""
REFERENCE_URL=""
EXPECTED_HTTP_STATUS=""
EXPECTED_ERROR_CODE=""
EXPECTED_ERROR_DESCRIPTION=""
API_URL="http://127.0.0.1:8080/api/testcase/generate"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-1}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --requirement)
      REQUIREMENT="${2:-}"
      shift 2
      ;;
    --reference-url)
      REFERENCE_URL="${2:-}"
      shift 2
      ;;
    --expected-http-status)
      EXPECTED_HTTP_STATUS="${2:-}"
      shift 2
      ;;
    --expected-error-code)
      EXPECTED_ERROR_CODE="${2:-}"
      shift 2
      ;;
    --expected-error-description)
      EXPECTED_ERROR_DESCRIPTION="${2:-}"
      shift 2
      ;;
    --api)
      API_URL="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "$REQUIREMENT" ]]; then
  echo "--requirement is required" >&2
  usage >&2
  exit 1
fi

PAYLOAD="$(python3 - "$REQUIREMENT" "$REFERENCE_URL" "$EXPECTED_HTTP_STATUS" "$EXPECTED_ERROR_CODE" "$EXPECTED_ERROR_DESCRIPTION" <<'PY'
import json
import sys

requirement, reference_url, expected_status, expected_code, expected_desc = sys.argv[1:]
payload = {"requirement": requirement}
if reference_url:
    payload["referenceUrl"] = reference_url
if expected_status:
    payload["expectedHttpStatus"] = int(expected_status)
if expected_code:
    payload["expectedErrorCode"] = expected_code
if expected_desc:
    payload["expectedErrorDescription"] = expected_desc
print(json.dumps(payload, ensure_ascii=False))
PY
)"

RESPONSE_FILE="$(mktemp /tmp/testcase-generate-response.XXXXXX.json)"
CLASS_FILE=""

cleanup() {
  if [[ "$KEEP_ARTIFACTS" != "1" ]]; then
    rm -f "$RESPONSE_FILE"
    if [[ -n "$CLASS_FILE" ]]; then
      rm -f "$CLASS_FILE"
    fi
  fi
}
trap cleanup EXIT

curl -sS -m 300 \
  -H 'Content-Type: application/json' \
  -X POST "$API_URL" \
  --data "$PAYLOAD" \
  > "$RESPONSE_FILE"

CLASS_NAME="$(
python3 - "$RESPONSE_FILE" "$EXPECTED_HTTP_STATUS" "$EXPECTED_ERROR_CODE" "$EXPECTED_ERROR_DESCRIPTION" <<'PY'
import json
import re
import sys
from pathlib import Path

response_path = Path(sys.argv[1])
expected_status = sys.argv[2]
expected_code = sys.argv[3]
expected_desc = sys.argv[4]

data = json.loads(response_path.read_text())
missing = [field for field in ("javaTestCode", "citations", "degraded", "refinedRequirement") if field not in data]
if missing:
    raise SystemExit(f"missing response fields: {', '.join(missing)}")

code = data["javaTestCode"]
citations = data["citations"]
if not isinstance(citations, list) or not citations:
    raise SystemExit("citations must be a non-empty list")
if len(citations) != 1:
    raise SystemExit(f"expected exactly one citation, found {len(citations)}")
if not str(data["refinedRequirement"]).strip():
    raise SystemExit("refinedRequirement must not be blank")

if "TODO" in code:
    raise SystemExit("generated code contains TODO")
if "placeholder" in code.lower():
    raise SystemExit("generated code contains placeholder token")
if "requiredConfig(" not in code:
    raise SystemExit("generated code does not use requiredConfig")

public_classes = re.findall(r"\bpublic\s+class\s+([A-Za-z_][A-Za-z0-9_]*)\b", code)
if len(public_classes) != 1:
    raise SystemExit(f"expected exactly one public class, found {len(public_classes)}")

hardcoded_literals = ["lite-123", "\"system\"", "'system'"]
for literal in hardcoded_literals:
    if literal in code:
        raise SystemExit(f"generated code contains fabricated resource literal: {literal}")

for env_key in [
    "HUAWEICLOUD_AUTH_TOKEN",
    "HUAWEICLOUD_PROJECT_ID",
    "HUAWEICLOUD_BASE_URL",
    "HUAWEICLOUD_DEV_SERVER_ID",
    "HUAWEICLOUD_SERVER_ID",
    "HUAWEICLOUD_INSTANCE_ID",
    "HUAWEICLOUD_VOLUME_ID",
    "HUAWEICLOUD_DISK_ID",
]:
    if f'System.getenv("{env_key}")' in code:
        raise SystemExit(f"generated code bypasses requiredConfig for {env_key}")

for property_key in [
    "hwcloud.auth.token",
    "hwcloud.project.id",
    "hwcloud.base.url",
    "hwcloud.dev-server.id",
    "hwcloud.server.id",
    "hwcloud.instance.id",
    "hwcloud.volume.id",
    "hwcloud.disk.id",
]:
    if f'System.getProperty("{property_key}")' in code:
        raise SystemExit(f"generated code bypasses requiredConfig for {property_key}")

if expected_status and expected_status not in code:
    raise SystemExit(f"generated code does not contain expected status: {expected_status}")
if expected_code and expected_code not in code:
    raise SystemExit(f"generated code does not contain expected error code: {expected_code}")
if expected_desc and expected_desc not in code:
    raise SystemExit("generated code does not contain expected error description")
if expected_status and expected_status not in str(data["refinedRequirement"]):
    raise SystemExit(f"refinedRequirement does not contain expected status: {expected_status}")
if expected_code and expected_code not in str(data["refinedRequirement"]):
    raise SystemExit(f"refinedRequirement does not contain expected error code: {expected_code}")
if expected_desc and expected_desc not in str(data["refinedRequirement"]):
    raise SystemExit("refinedRequirement does not contain expected error description")

print(public_classes[0])
PY
)"

CLASS_FILE="/tmp/${CLASS_NAME}.java"
python3 - "$RESPONSE_FILE" "$CLASS_FILE" <<'PY'
import json
import sys
from pathlib import Path

response_path = Path(sys.argv[1])
class_path = Path(sys.argv[2])
code = json.loads(response_path.read_text())["javaTestCode"]
class_path.write_text(code)
PY

JAVA_HOME_CANDIDATE="/usr/lib/jvm/java-21-openjdk-amd64"
JAVAC_BIN="${JAVA_HOME_CANDIDATE}/bin/javac"
if [[ ! -x "$JAVAC_BIN" ]]; then
  JAVAC_BIN="$(command -v javac)"
fi
if [[ -z "$JAVAC_BIN" || ! -x "$JAVAC_BIN" ]]; then
  echo "javac not found" >&2
  exit 1
fi

JUNIT_API="$HOME/.m2/repository/org/junit/jupiter/junit-jupiter-api/5.10.1/junit-jupiter-api-5.10.1.jar"
JUNIT_COMMONS="$HOME/.m2/repository/org/junit/platform/junit-platform-commons/1.10.1/junit-platform-commons-1.10.1.jar"
OPENTEST4J="$HOME/.m2/repository/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar"
APIGUARDIAN="$HOME/.m2/repository/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar"

for dep in "$JUNIT_API" "$JUNIT_COMMONS" "$OPENTEST4J" "$APIGUARDIAN"; do
  if [[ ! -f "$dep" ]]; then
    echo "missing compile dependency: $dep" >&2
    exit 1
  fi
done

"$JAVAC_BIN" \
  -cp "$JUNIT_API:$JUNIT_COMMONS:$OPENTEST4J:$APIGUARDIAN" \
  "$CLASS_FILE"

echo "verification passed"
echo "response: $RESPONSE_FILE"
echo "class: $CLASS_FILE"
