#!/usr/bin/env bash
# Build Lunch Heir from the pristine upstream Lawnchair submodule + the Lunch Heir overlay.
#
# Usage:
#   ./build-lunchheir.sh                       # assembles the github debug variant
#   ./build-lunchheir.sh assembleLawnWithQuickstepGithubRelease
#   ./build-lunchheir.sh <any gradle args...>
#
# Requires: the upstream submodule initialized (git submodule update --init --recursive),
# a JDK 21, and the Android SDK (ANDROID_HOME / local.properties as upstream expects).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ ! -e "$ROOT/upstream/settings.gradle" ]; then
  echo "upstream submodule not initialized — running: git submodule update --init --recursive"
  git -C "$ROOT" submodule update --init --recursive
fi

# Apply the source-level overlay (backup compatibility) to the submodule working tree.
"$ROOT/overlay/apply-overlay.sh"

TASK_ARGS=("$@")
if [ ${#TASK_ARGS[@]} -eq 0 ]; then
  TASK_ARGS=(assembleLawnWithQuickstepGithubDebug)
fi

# If the SDK location is configured at the repo root, hand it to the nested build.
if [ -f "$ROOT/local.properties" ] && [ ! -f "$ROOT/upstream/local.properties" ]; then
  cp "$ROOT/local.properties" "$ROOT/upstream/local.properties"
fi

# Build with upstream as the Gradle root. The overlay (branding + source dirs) is applied
# by overlay/apply_overlay.py, which appends an `apply from:` line to upstream/build.gradle.
cd "$ROOT/upstream"
chmod +x gradlew
exec ./gradlew "${TASK_ARGS[@]}"
