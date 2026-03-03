# CannyMinute Docker Android Build/Test Pipeline

## Goals
- Build and run tests with minimal host dependencies.
- Use a dockerized Android emulator (`budtmo/docker-android`) for instrumentation tests.
- Keep unit and instrumentation runs scriptable for local and CI usage.
- Split instrumentation into two phases for stability:
  1. Build APK artifacts in a builder container.
  2. Install and execute tests on emulator via `adb`.

## What was added
- Base emulator compose file: `docker-compose.android.yml`
- KVM override compose file: `docker-compose.android.kvm.yml`
- Custom docker image (adds JDK 17 for AGP 8.5+):
  - `docker/android-test/Dockerfile`
  - Pre-installs SDK components required by this project (`platforms;android-35`, `build-tools;34.0.0`)
  - Uses `budtmo/docker-android:emulator_13.0` for better local kernel stability than Android 14 in our environment.
- Local scripts:
  - `scripts/android/run-unit-tests.sh`
  - `scripts/android/run-instrumented-tests.sh`
  - `scripts/android/run-all-tests.sh`
  - `scripts/android/run-unit-tests.ps1`
  - `scripts/android/run-instrumented-tests.ps1`
  - `scripts/android/run-all-tests.ps1`
- CI workflow:
  - `.github/workflows/android-build-and-test.yml`

## Prerequisites
- Docker Desktop (or Docker Engine + Compose v2).
- For emulator acceleration: Linux/WSL2 with `/dev/kvm` available to containers.
- Enough host resources for emulator:
  - 4+ CPU cores
  - 8+ GB RAM free
  - 15+ GB free disk

## Local usage (PowerShell)
Run unit tests:
```powershell
./scripts/android/run-unit-tests.ps1
```

Run instrumentation tests:
```powershell
./scripts/android/run-instrumented-tests.ps1
```

Run all:
```powershell
./scripts/android/run-all-tests.ps1
```

## Local usage (bash)
Run unit tests:
```bash
./scripts/android/run-unit-tests.sh
```

Run instrumentation tests:
```bash
./scripts/android/run-instrumented-tests.sh
```

Run all:
```bash
./scripts/android/run-all-tests.sh
```

## CI behavior
- `unit-tests` job runs on `ubuntu-latest` using Docker.
- `instrumented-tests` job is manual (`workflow_dispatch`) and runs only when:
  - `run_instrumented = true`
  - runner labels include: `self-hosted`, `linux`, `x64`, `kvm`

## Runner notes for instrumentation
- Self-hosted Linux runner is strongly recommended.
- Runner user must be able to run Docker.
- `/dev/kvm` must be exposed to Docker.
- This setup requires KVM for reliable emulator startup. If `/dev/kvm` is missing, scripts fail fast with a clear error.

## Emulator stability profile (default)
`docker-compose.android.yml` now uses a stability-first emulator config:
- `-gpu swiftshader_indirect` (software-rendered GPU path)
- `-no-snapshot`, `-no-snapshot-load`, `-no-snapshot-save`
- `-noaudio`, `-no-boot-anim`
- `-camera-back none`, `-camera-front none`
- `-cores 2`, `-memory 2048`

These defaults are intended to reduce random emulator reboots/panics during checkout-flow testing.

## Expected outputs
- Unit test reports under `app/build/reports/tests`.
- Instrumentation reports under:
  - `app/build/reports/androidTests`
  - `app/build/outputs/androidTest-results`

## Next hardening tasks
1. Add `connectedDebugAndroidTest` shard strategy for faster parallel suites.
2. Publish a base prebuilt `cannyminute/android-test` image to GHCR to reduce CI cold-start times.
3. Add retry wrapper around flaky emulator boot and first-run Gradle dependency resolution.
