#!/usr/bin/env bash
# seed.sh — delegates to seed.py.  Usage: ./scripts/seed.sh [BASE_URL]
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "$SCRIPT_DIR/seed.py" "$@"
