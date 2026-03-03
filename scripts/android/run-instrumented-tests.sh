#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

CONTAINER_NAME="cannyminute-android-emulator"
KEEP_EMULATOR="${KEEP_EMULATOR:-false}"
ACTIVE_COMPOSE_ARGS=("-f" "docker-compose.android.yml" "-f" "docker-compose.android.kvm.yml")
APP_APK_PATH="/workspace/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK_PATH="/workspace/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
INSTRUMENTATION_TARGET="com.projectz.cannyminute.test/androidx.test.runner.AndroidJUnitRunner"

cd "${ROOT_DIR}"

cleanup() {
  if [[ "${KEEP_EMULATOR}" == "true" ]]; then
    echo "[androidTest] KEEP_EMULATOR=true, leaving emulator running."
    return
  fi
  echo "[androidTest] Stopping emulator container..."
  docker compose "${ACTIVE_COMPOSE_ARGS[@]}" down --remove-orphans || true
}
trap cleanup EXIT

container_running() {
  docker ps -q -f "name=^${CONTAINER_NAME}$" | grep -q .
}

print_logs() {
  echo "[androidTest] Emulator logs (tail):"
  docker logs --tail 200 "${CONTAINER_NAME}" || true
}

echo "[androidTest] Building docker image (cannyminute/android-test:local)..."
docker compose -f docker-compose.android.yml build emulator

echo "[androidTest] Building app + androidTest APKs..."
docker run --rm \
  --user 0 \
  -v "${ROOT_DIR}:/workspace" \
  -w /workspace \
  --entrypoint /bin/bash \
  cannyminute/android-test:local \
  -lc "./gradlew --no-daemon --stacktrace assembleDebug assembleDebugAndroidTest"

echo "[androidTest] Starting emulator (KVM required)..."
docker compose "${ACTIVE_COMPOSE_ARGS[@]}" up -d emulator

echo "[androidTest] Waiting for emulator readiness..."
READY="false"
for i in $(seq 1 120); do
  if ! container_running; then
    LOGS="$(docker logs "${CONTAINER_NAME}" 2>&1 || true)"
    if [[ "${LOGS}" == *"/dev/kvm cannot be found!"* ]]; then
      echo "[androidTest] /dev/kvm is not available to Docker."
      echo "[androidTest] Enable nested virtualization / KVM passthrough and retry."
    fi
    echo "[androidTest] Emulator container stopped unexpectedly."
    print_logs
    exit 1
  fi
  STATUS="$(docker exec "${CONTAINER_NAME}" bash -lc "cat /home/androidusr/device_status 2>/dev/null || cat device_status 2>/dev/null || true" | tr -d '\r\n' || true)"
  ADB_STATE="$(docker exec "${CONTAINER_NAME}" bash -lc "adb get-state 2>/dev/null || true" | tr -d '\r\n' || true)"
  BOOT_COMPLETED="$(docker exec "${CONTAINER_NAME}" bash -lc "adb shell getprop sys.boot_completed 2>/dev/null || true" | tr -d '\r\n' || true)"
  PACKAGE_SERVICE="$(docker exec "${CONTAINER_NAME}" bash -lc "adb shell service check package 2>/dev/null || true" | tr -d '\r\n' || true)"
  if [[ ("${STATUS}" == "device" || "${STATUS}" == "READY" || "${ADB_STATE}" == "device") && "${BOOT_COMPLETED}" == "1" && "${PACKAGE_SERVICE}" == *"found"* ]]; then
    READY="true"
    break
  fi
  sleep 5
done

if [[ "${READY}" != "true" ]]; then
  echo "[androidTest] Emulator did not become ready in time."
  print_logs
  exit 1
fi

echo "[androidTest] Installing APKs..."
docker exec "${CONTAINER_NAME}" adb install -r "${APP_APK_PATH}"
docker exec "${CONTAINER_NAME}" adb install -r "${TEST_APK_PATH}"

echo "[androidTest] Running instrumentation via adb..."
docker exec "${CONTAINER_NAME}" adb shell am instrument -w "${INSTRUMENTATION_TARGET}"

echo "[androidTest] Instrumentation tests completed."
