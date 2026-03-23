#!/usr/bin/env bash
set -euo pipefail

VENV_DIR="${CHROMA_VENV_DIR:-/tmp/chroma-venv-0520}"
DATA_DIR="${CHROMA_DATA_DIR:-/tmp/chroma-data-22333}"
LOG_FILE="${CHROMA_LOG_FILE:-/tmp/chroma-22333.log}"
PID_FILE="${CHROMA_PID_FILE:-/tmp/chroma-22333.pid}"
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

mkdir -p "$DATA_DIR"

setsid nohup "$CHROMA_BIN" run --host "$HOST" --port "$PORT" --path "$DATA_DIR" \
  >"$LOG_FILE" 2>&1 < /dev/null &
PID=$!
echo "$PID" >"$PID_FILE"

sleep 2

if ! kill -0 "$PID" 2>/dev/null; then
  echo "chroma failed to stay up; log: $LOG_FILE" >&2
  tail -n 40 "$LOG_FILE" >&2 || true
  exit 1
fi

echo "chroma pid: $PID"
echo "data dir: $DATA_DIR"
echo "log file: $LOG_FILE"
echo "pid file: $PID_FILE"
echo "heartbeat v1: curl -i http://$HOST:$PORT/api/v1/heartbeat"
echo "heartbeat v2: curl -i http://$HOST:$PORT/api/v2/heartbeat"
