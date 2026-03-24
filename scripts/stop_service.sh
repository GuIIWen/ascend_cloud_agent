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
SERVER_PORT="${ASCEND_AGENT_PORT:-8080}"

PID=""
if [[ -f "$PID_FILE" ]]; then
  PID="$(tr -d '[:space:]' <"$PID_FILE")"
fi

resolve_port_pid() {
  if ! command -v lsof >/dev/null 2>&1; then
    return 0
  fi

  lsof -ti TCP:"$SERVER_PORT" -sTCP:LISTEN 2>/dev/null | head -n 1 || true
}

is_agent_process() {
  local candidate="$1"
  local args

  if [[ -z "$candidate" ]]; then
    return 1
  fi

  args="$(ps -p "$candidate" -o args= 2>/dev/null || true)"
  [[ "$args" == *ascend-agent* || "$args" == *com.agent.AscendAgentApplication* ]]
}

if [[ -z "$PID" ]] || ! kill -0 "$PID" 2>/dev/null; then
  PORT_PID="$(resolve_port_pid)"
  if is_agent_process "$PORT_PID"; then
    PID="$PORT_PID"
  else
    echo "service is not running; pid file: $PID_FILE port: $SERVER_PORT"
    rm -f "$PID_FILE"
    exit 0
  fi
fi

kill "$PID"

for _ in $(seq 1 "$WAIT_SECONDS"); do
  PORT_PID="$(resolve_port_pid)"
  if ! kill -0 "$PID" 2>/dev/null && [[ -z "$PORT_PID" ]]; then
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
