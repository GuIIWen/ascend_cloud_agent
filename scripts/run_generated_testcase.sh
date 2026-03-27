#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  bash scripts/run_generated_testcase.sh \
    --requirement "..." \
    [--reference-url "https://..."] \
    [--expected-http-status 400] \
    [--expected-error-code ModelArts.7000] \
    [--expected-error-description "..."] \
    [--api http://127.0.0.1:8080/api/testcase/generate] \
    [--execute]

Modes:
  default     compile-only: generate code, validate it, and compile it with Java 21
  --execute   compile + execute the generated JUnit test with the current environment

Notes:
  - execute mode uses the env vars or system properties expected by the generated testcase
  - the script prepares GeneratedJUnitRunner automatically via scripts/install_generated_test_runner.sh
EOF
}

REQUIREMENT=""
REFERENCE_URL=""
EXPECTED_HTTP_STATUS=""
EXPECTED_ERROR_CODE=""
EXPECTED_ERROR_DESCRIPTION=""
API_URL="http://127.0.0.1:8080/api/testcase/generate"
MODE="compile-only"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-1}"
AGENT_HOME="${ASCEND_AGENT_HOME:-$(pwd)/.ascend_agent}"
RUNNER_HOME="${ASCEND_AGENT_RUNNER_HOME:-$AGENT_HOME/tools/generated-test-runner}"
RUNS_ROOT="${ASCEND_AGENT_GENERATED_TEST_RUNS:-$AGENT_HOME/generated-testcase-runs}"
DEFAULT_JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"

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
    --execute)
      MODE="execute"
      shift
      ;;
    --compile-only)
      MODE="compile-only"
      shift
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

extract_java_major() {
  local java_bin="$1"
  "$java_bin" -version 2>&1 | awk -F '[\".]' '/version/ { if ($2 == "1") { print $3 } else { print $2 } exit }'
}

resolve_java_tool() {
  local tool="$1"
  local default_candidate="$DEFAULT_JAVA_HOME/bin/$tool"
  local env_candidate=""
  local command_candidate=""
  local major=""

  if [[ -x "$DEFAULT_JAVA_HOME/bin/java" && -x "$default_candidate" ]]; then
    major="$(extract_java_major "$DEFAULT_JAVA_HOME/bin/java")"
    if [[ -n "$major" && "$major" -ge 21 ]]; then
      printf '%s\n' "$default_candidate"
      return 0
    fi
  fi

  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" && -x "${JAVA_HOME}/bin/$tool" ]]; then
    env_candidate="${JAVA_HOME}/bin/$tool"
    major="$(extract_java_major "${JAVA_HOME}/bin/java")"
    if [[ -n "$major" && "$major" -ge 21 ]]; then
      printf '%s\n' "$env_candidate"
      return 0
    fi
  fi

  command_candidate="$(command -v "$tool" || true)"
  if [[ -n "$command_candidate" ]]; then
    local java_bin
    java_bin="$(dirname "$command_candidate")/java"
    if [[ -x "$java_bin" ]]; then
      major="$(extract_java_major "$java_bin")"
      if [[ -n "$major" && "$major" -ge 21 ]]; then
        printf '%s\n' "$command_candidate"
        return 0
      fi
    fi
  fi
  return 1
}

JAVAC_BIN="$(resolve_java_tool javac || true)"
JAVA_BIN="$(resolve_java_tool java || true)"
if [[ -z "$JAVAC_BIN" || ! -x "$JAVAC_BIN" ]]; then
  echo "javac for Java 21 not found; install Java 21 first" >&2
  exit 1
fi
if [[ -z "$JAVA_BIN" || ! -x "$JAVA_BIN" ]]; then
  echo "java for Java 21 not found; install Java 21 first" >&2
  exit 1
fi

bash scripts/install_generated_test_runner.sh >/dev/null

if [[ ! -f "$RUNNER_HOME/junit.classpath" ]]; then
  echo "missing runner classpath file: $RUNNER_HOME/junit.classpath" >&2
  exit 1
fi
if [[ ! -d "$RUNNER_HOME/classes" ]]; then
  echo "missing runner classes: $RUNNER_HOME/classes" >&2
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

RUN_ID="$(date +%Y%m%d-%H%M%S)-$$"
RUN_DIR="$RUNS_ROOT/$RUN_ID"
RESPONSE_FILE="$RUN_DIR/response.json"
METADATA_FILE="$RUN_DIR/metadata.json"
SRC_ROOT="$RUN_DIR/src"
CLASSES_DIR="$RUN_DIR/classes"

cleanup() {
  if [[ "$KEEP_ARTIFACTS" != "1" ]]; then
    rm -rf "$RUN_DIR"
  fi
}
trap cleanup EXIT

mkdir -p "$RUN_DIR" "$SRC_ROOT" "$CLASSES_DIR"

curl -sS -m 300 \
  -H 'Content-Type: application/json' \
  -X POST "$API_URL" \
  --data "$PAYLOAD" \
  > "$RESPONSE_FILE"

python3 - "$RESPONSE_FILE" "$METADATA_FILE" "$SRC_ROOT" "$EXPECTED_HTTP_STATUS" "$EXPECTED_ERROR_CODE" "$EXPECTED_ERROR_DESCRIPTION" <<'PY'
import json
import re
import sys
from pathlib import Path

response_path = Path(sys.argv[1])
metadata_path = Path(sys.argv[2])
src_root = Path(sys.argv[3])
expected_status = sys.argv[4]
expected_code = sys.argv[5]
expected_desc = sys.argv[6]

data = json.loads(response_path.read_text())
missing = [field for field in ("javaTestCode", "citations", "degraded", "refinedRequirement") if field not in data]
if missing:
    raise SystemExit(f"missing response fields: {', '.join(missing)}")

code = data["javaTestCode"]
if not isinstance(data["citations"], list) or not data["citations"]:
    raise SystemExit("citations must be a non-empty list")
if "TODO" in code:
    raise SystemExit("generated code contains TODO")
if "placeholder" in code.lower():
    raise SystemExit("generated code contains placeholder token")
if "requiredConfig(" not in code:
    raise SystemExit("generated code does not use requiredConfig")

public_classes = re.findall(r"\bpublic\s+class\s+([A-Za-z_][A-Za-z0-9_]*)\b", code)
if len(public_classes) != 1:
    raise SystemExit(f"expected exactly one public class, found {len(public_classes)}")
class_name = public_classes[0]

package_match = re.search(r"^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)\s*;", code, re.MULTILINE)
package_name = package_match.group(1) if package_match else ""
fqcn = f"{package_name}.{class_name}" if package_name else class_name

for literal in ["lite-123", "\"system\"", "'system'"]:
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

refined_requirement = str(data["refinedRequirement"])
if not refined_requirement.strip():
    raise SystemExit("refinedRequirement must not be blank")

if expected_status and expected_status not in code:
    raise SystemExit(f"generated code does not contain expected status: {expected_status}")
if expected_code and expected_code not in code:
    raise SystemExit(f"generated code does not contain expected error code: {expected_code}")
if expected_desc and expected_desc not in code:
    raise SystemExit("generated code does not contain expected error description")
if expected_status and expected_status not in refined_requirement:
    raise SystemExit(f"refinedRequirement does not contain expected status: {expected_status}")
if expected_code and expected_code not in refined_requirement:
    raise SystemExit(f"refinedRequirement does not contain expected error code: {expected_code}")
if expected_desc and expected_desc not in refined_requirement:
    raise SystemExit("refinedRequirement does not contain expected error description")

source_dir = src_root / Path(package_name.replace(".", "/")) if package_name else src_root
source_dir.mkdir(parents=True, exist_ok=True)
source_file = source_dir / f"{class_name}.java"
source_file.write_text(code)

metadata = {
    "className": class_name,
    "packageName": package_name,
    "fqcn": fqcn,
    "sourceFile": str(source_file),
}
metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2))
PY

FQCN="$(python3 - "$METADATA_FILE" <<'PY'
import json
import sys
from pathlib import Path
print(json.loads(Path(sys.argv[1]).read_text())["fqcn"])
PY
)"
SOURCE_FILE="$(python3 - "$METADATA_FILE" <<'PY'
import json
import sys
from pathlib import Path
print(json.loads(Path(sys.argv[1]).read_text())["sourceFile"])
PY
)"

RUNNER_CP="$(cat "$RUNNER_HOME/junit.classpath")"
COMPILE_CP="$RUNNER_CP:$RUNNER_HOME/classes"

"$JAVAC_BIN" -cp "$COMPILE_CP" -d "$CLASSES_DIR" "$SOURCE_FILE"

echo "generation response: $RESPONSE_FILE"
echo "generated source: $SOURCE_FILE"
echo "compiled classes: $CLASSES_DIR"
echo "mode: $MODE"

if [[ "$MODE" == "execute" ]]; then
  EXEC_CP="$RUNNER_HOME/classes:$CLASSES_DIR:$RUNNER_CP"
  "$JAVA_BIN" -cp "$EXEC_CP" GeneratedJUnitRunner "$FQCN"
fi
