#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
AGENT_HOME="${ASCEND_AGENT_HOME:-$REPO_ROOT/.ascend_agent}"
VENV_DIR="${CHROMA_VENV_DIR:-$AGENT_HOME/tools/chroma-venv-0520}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
CHROMADB_VERSION="${CHROMADB_VERSION:-0.5.20}"

if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  echo "python executable not found: $PYTHON_BIN" >&2
  exit 1
fi

if [[ ! -d "$VENV_DIR" ]]; then
  mkdir -p "$(dirname "$VENV_DIR")"
  "$PYTHON_BIN" -m venv "$VENV_DIR"
fi

"$VENV_DIR/bin/pip" install --upgrade pip
"$VENV_DIR/bin/pip" install "chromadb==${CHROMADB_VERSION}"

INSTALLED_VERSION="$("$VENV_DIR/bin/python" - <<'PY'
import chromadb
print(chromadb.__version__)
PY
)"

echo "agent home: $AGENT_HOME"
echo "chroma venv: $VENV_DIR"
echo "chromadb version: $INSTALLED_VERSION"
echo "binary: $VENV_DIR/bin/chroma"
