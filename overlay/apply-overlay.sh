#!/usr/bin/env bash
# Apply Lunch Heir's source-level overlay edits to the upstream submodule.
# Idempotent; re-run after every `git submodule update`.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec python3 "$ROOT/overlay/apply_overlay.py" "$ROOT/upstream"
