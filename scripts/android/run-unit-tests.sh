#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

cd "${ROOT_DIR}"

echo "[unit] Building docker image (cannyminute/android-test:local)..."
docker compose -f docker-compose.android.yml build emulator

echo "[unit] Running testDebugUnitTest inside docker image..."
docker run --rm \
  --user 0 \
  -v "${ROOT_DIR}:/workspace" \
  -w /workspace \
  --entrypoint /bin/bash \
  cannyminute/android-test:local \
  -lc "./gradlew --no-daemon --stacktrace testDebugUnitTest"

echo "[unit] Unit tests completed."
