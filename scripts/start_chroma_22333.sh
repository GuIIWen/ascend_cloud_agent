#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
AGENT_HOME="${ASCEND_AGENT_HOME:-$REPO_ROOT/.ascend_agent}"
VENV_DIR="${CHROMA_VENV_DIR:-$AGENT_HOME/tools/chroma-venv-0520}"
DATA_DIR="${CHROMA_DATA_DIR:-$AGENT_HOME/chroma}"
LOG_DIR="${CHROMA_LOG_DIR:-$AGENT_HOME/logs}"
PID_DIR="${CHROMA_PID_DIR:-$AGENT_HOME/pids}"
LOG_FILE="${CHROMA_LOG_FILE:-$LOG_DIR/chroma-22333.log}"
PID_FILE="${CHROMA_PID_FILE:-$PID_DIR/chroma-22333.pid}"
HOST="${CHROMA_HOST:-127.0.0.1}"
PORT="${CHROMA_PORT:-22333}"
CHROMA_BIN="$VENV_DIR/bin/chroma"

if [[ ! -x "$CHROMA_BIN" ]]; then
  echo "missing chroma binary: $CHROMA_BIN" >&2
  echo "run scripts/install_chroma_0520.sh first" >&2
  exit 1
fi

if command -v lsof >/dev/null 2>&1 && lsof -i :"$PORT" -P -n >/dev/null 2>&1; then
  echo "port $PORT is already in use" >&2
  lsof -i :"$PORT" -P -n >&2 || true
  exit 1
fi

mkdir -p "$DATA_DIR" "$LOG_DIR" "$PID_DIR"

setsid nohup "$CHROMA_BIN" run --host "$HOST" --port "$PORT" --path "$DATA_DIR" \
  >"$LOG_FILE" 2>&1 < /dev/null &
PID=$!

sleep 2

if ! kill -0 "$PID" 2>/dev/null; then
  echo "chroma failed to stay up; log: $LOG_FILE" >&2
  tail -n 40 "$LOG_FILE" >&2 || true
  exit 1
fi

REAL_PID="$PID"
if command -v lsof >/dev/null 2>&1; then
  LISTEN_PID="$(lsof -ti TCP:"$PORT" -sTCP:LISTEN 2>/dev/null | head -n 1 || true)"
  if [[ -n "$LISTEN_PID" ]]; then
    REAL_PID="$LISTEN_PID"
  fi
fi

echo "$REAL_PID" >"$PID_FILE"

echo "agent home: $AGENT_HOME"
echo "chroma pid: $REAL_PID"
echo "data dir: $DATA_DIR"
echo "log file: $LOG_FILE"
echo "pid file: $PID_FILE"
echo "heartbeat v1: curl -i http://$HOST:$PORT/api/v1/heartbeat"
echo "heartbeat v2: curl -i http://$HOST:$PORT/api/v2/heartbeat"
