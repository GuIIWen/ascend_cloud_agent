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
CHROMA_URL="${ASCEND_AGENT_CHROMA_URL:-http://127.0.0.1:22333}"
CHROMA_COLLECTION="${ASCEND_AGENT_CHROMA_COLLECTION:-api-knowledge-base}"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "missing jar: $JAR_PATH" >&2
  echo "build first: timeout 180 mvn -q -DskipTests package" >&2
  exit 1
fi

if command -v lsof >/dev/null 2>&1 && lsof -i :"$SERVER_PORT" -P -n >/dev/null 2>&1; then
  echo "port $SERVER_PORT is already in use" >&2
  lsof -i :"$SERVER_PORT" -P -n >&2 || true
  exit 1
fi

mkdir -p "$APP_DATA_DIR" "$LOG_DIR" "$PID_DIR"
export PATH="$JAVA_HOME/bin:$PATH"

setsid nohup java \
  -Dascend.agent.home="$AGENT_HOME" \
  -Dascend.agent.data-dir="$APP_DATA_DIR" \
  -jar "$JAR_PATH" \
  --server.port="$SERVER_PORT" \
  --knowledge-base.vector-store.type=chroma \
  --knowledge-base.vector-store.url="$CHROMA_URL" \
  --knowledge-base.vector-store.collection="$CHROMA_COLLECTION" \
  >"$LOG_FILE" 2>&1 < /dev/null &
PID=$!

sleep 2

if ! kill -0 "$PID" 2>/dev/null; then
  echo "service failed to stay up; log: $LOG_FILE" >&2
  tail -n 60 "$LOG_FILE" >&2 || true
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
echo "app data dir: $APP_DATA_DIR"
echo "log file: $LOG_FILE"
echo "pid file: $PID_FILE"
echo "health: curl -i http://127.0.0.1:$SERVER_PORT/actuator/health"
