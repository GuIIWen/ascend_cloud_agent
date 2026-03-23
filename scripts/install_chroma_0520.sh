#!/usr/bin/env bash
set -euo pipefail

VENV_DIR="${CHROMA_VENV_DIR:-/tmp/chroma-venv-0520}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
CHROMADB_VERSION="${CHROMADB_VERSION:-0.5.20}"

if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  echo "python executable not found: $PYTHON_BIN" >&2
  exit 1
fi

if [[ ! -d "$VENV_DIR" ]]; then
  "$PYTHON_BIN" -m venv "$VENV_DIR"
fi

"$VENV_DIR/bin/pip" install --upgrade pip
"$VENV_DIR/bin/pip" install "chromadb==${CHROMADB_VERSION}"

INSTALLED_VERSION="$("$VENV_DIR/bin/python" - <<'PY'
import chromadb
print(chromadb.__version__)
PY
)"

echo "chroma venv: $VENV_DIR"
echo "chromadb version: $INSTALLED_VERSION"
echo "binary: $VENV_DIR/bin/chroma"
