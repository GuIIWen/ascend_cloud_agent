#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
AGENT_HOME="${ASCEND_AGENT_HOME:-$REPO_ROOT/.ascend_agent}"
PID_DIR="${ASCEND_AGENT_PID_DIR:-$AGENT_HOME/pids}"
PID_FILE="${ASCEND_AGENT_PID_FILE:-$PID_DIR/service.pid}"
LOG_DIR="${ASCEND_AGENT_LOG_DIR:-$AGENT_HOME/logs}"
LOG_FILE="${ASCEND_AGENT_LOG_FILE:-$LOG_DIR/service.log}"
WAIT_SECONDS="${ASCEND_AGENT_STOP_WAIT_SECONDS:-20}"

if [[ ! -f "$PID_FILE" ]]; then
  echo "missing pid file: $PID_FILE" >&2
  exit 1
fi

PID="$(tr -d '[:space:]' <"$PID_FILE")"
if [[ -z "$PID" ]]; then
  echo "empty pid file: $PID_FILE" >&2
  exit 1
fi

if ! kill -0 "$PID" 2>/dev/null; then
  echo "process not running: $PID"
  rm -f "$PID_FILE"
  exit 0
fi

kill "$PID"

for _ in $(seq 1 "$WAIT_SECONDS"); do
  if ! kill -0 "$PID" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "service stopped: $PID"
    echo "log file: $LOG_FILE"
    exit 0
  fi
  sleep 1
done

echo "service did not stop within ${WAIT_SECONDS}s; sending SIGKILL: $PID" >&2
kill -9 "$PID"
rm -f "$PID_FILE"
echo "service killed: $PID"
echo "log file: $LOG_FILE"
