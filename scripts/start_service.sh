#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
AGENT_HOME="${ASCEND_AGENT_HOME:-$REPO_ROOT/.ascend_agent}"
APP_DATA_DIR="${ASCEND_AGENT_DATA_DIR:-$AGENT_HOME/db}"
LOG_DIR="${ASCEND_AGENT_LOG_DIR:-$AGENT_HOME/logs}"
PID_DIR="${ASCEND_AGENT_PID_DIR:-$AGENT_HOME/pids}"
LOG_FILE="${ASCEND_AGENT_LOG_FILE:-$LOG_DIR/service.log}"
PID_FILE="${ASCEND_AGENT_PID_FILE:-$PID_DIR/service.pid}"
JAR_PATH="${ASCEND_AGENT_JAR:-$REPO_ROOT/target/ascend-agent-1.0.0.jar}"
SERVER_PORT="${ASCEND_AGENT_PORT:-8080}"
HEALTH_PATH="${ASCEND_AGENT_HEALTH_PATH:-/actuator/health}"
START_TIMEOUT="${ASCEND_AGENT_START_TIMEOUT_SECONDS:-30}"
CHROMA_URL="${ASCEND_AGENT_CHROMA_URL:-http://127.0.0.1:22333}"
CHROMA_COLLECTION="${ASCEND_AGENT_CHROMA_COLLECTION:-api-knowledge-base}"
DEFAULT_JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
JAVA_HOME_CANDIDATE="${ASCEND_AGENT_JAVA_HOME:-${JAVA_HOME:-$DEFAULT_JAVA_HOME}}"
HEALTH_URL="http://127.0.0.1:$SERVER_PORT$HEALTH_PATH"

extract_java_major() {
  local java_bin="$1"
  "$java_bin" -version 2>&1 | awk -F '[\".]' '/version/ { if ($2 == "1") { print $3 } else { print $2 } exit }'
}

resolve_java_bin() {
  local candidate="${ASCEND_AGENT_JAVA_BIN:-}"
  local candidate_major=""
  local fallback="$DEFAULT_JAVA_HOME/bin/java"

  if [[ -z "$candidate" ]]; then
    candidate="$JAVA_HOME_CANDIDATE/bin/java"
  fi

  if [[ ! -x "$candidate" ]]; then
    candidate=""
  else
    candidate_major="$(extract_java_major "$candidate")"
    if [[ -z "$candidate_major" || "$candidate_major" -lt 21 ]]; then
      candidate=""
    fi
  fi

  if [[ -z "$candidate" && -x "$fallback" ]]; then
    candidate="$fallback"
  fi

  if [[ -z "$candidate" || ! -x "$candidate" ]]; then
    echo "unable to resolve a Java 21 runtime; checked ASCEND_AGENT_JAVA_BIN, ASCEND_AGENT_JAVA_HOME, JAVA_HOME, and $DEFAULT_JAVA_HOME" >&2
    exit 1
  fi

  echo "$candidate"
}

JAVA_BIN="$(resolve_java_bin)"
JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
JAVA_MAJOR="$(extract_java_major "$JAVA_BIN")"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "missing jar: $JAR_PATH" >&2
  echo "build first: timeout 180 mvn -q -DskipTests package" >&2
  exit 1
fi

if [[ -z "$JAVA_MAJOR" || "$JAVA_MAJOR" -lt 21 ]]; then
  echo "resolved Java runtime is too old: $JAVA_BIN (major=$JAVA_MAJOR)" >&2
  exit 1
fi

if command -v lsof >/dev/null 2>&1 && lsof -i :"$SERVER_PORT" -P -n >/dev/null 2>&1; then
  echo "port $SERVER_PORT is already in use" >&2
  lsof -i :"$SERVER_PORT" -P -n >&2 || true
  exit 1
fi

mkdir -p "$APP_DATA_DIR" "$LOG_DIR" "$PID_DIR"
export PATH="$JAVA_HOME/bin:$PATH"

setsid nohup "$JAVA_BIN" \
  -Dascend.agent.home="$AGENT_HOME" \
  -Dascend.agent.data-dir="$APP_DATA_DIR" \
  -jar "$JAR_PATH" \
  --server.port="$SERVER_PORT" \
  --spring.main.register-shutdown-hook=false \
  --logging.register-shutdown-hook=false \
  --knowledge-base.vector-store.type=chroma \
  --knowledge-base.vector-store.url="$CHROMA_URL" \
  --knowledge-base.vector-store.collection="$CHROMA_COLLECTION" \
  >"$LOG_FILE" 2>&1 < /dev/null &
PID=$!

for _ in $(seq 1 "$START_TIMEOUT"); do
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "service exited during startup; log: $LOG_FILE" >&2
    tail -n 80 "$LOG_FILE" >&2 || true
    exit 1
  fi
  if curl -fsS -m 2 "$HEALTH_URL" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! curl -fsS -m 2 "$HEALTH_URL" >/dev/null 2>&1; then
  echo "service did not become healthy within ${START_TIMEOUT}s; log: $LOG_FILE" >&2
  kill "$PID" 2>/dev/null || true
  sleep 1
  tail -n 80 "$LOG_FILE" >&2 || true
  exit 1
fi

REAL_PID="$PID"
if command -v lsof >/dev/null 2>&1; then
  LISTEN_PID="$(lsof -ti TCP:"$SERVER_PORT" -sTCP:LISTEN 2>/dev/null | head -n 1 || true)"
  if [[ -n "$LISTEN_PID" ]]; then
    REAL_PID="$LISTEN_PID"
  fi
fi

echo "$REAL_PID" >"$PID_FILE"

echo "agent home: $AGENT_HOME"
echo "service pid: $REAL_PID"
echo "java: $JAVA_BIN"
echo "app data dir: $APP_DATA_DIR"
echo "log file: $LOG_FILE"
echo "pid file: $PID_FILE"
echo "health: curl -i $HEALTH_URL"
echo "info: curl -i http://127.0.0.1:$SERVER_PORT/actuator/info"
