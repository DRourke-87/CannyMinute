param(
    [switch]$KeepEmulator
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$containerName = "cannyminute-android-emulator"
$activeComposeArgs = @("-f", "docker-compose.android.yml", "-f", "docker-compose.android.kvm.yml")
$appApkPath = "/workspace/app/build/outputs/apk/debug/app-debug.apk"
$testApkPath = "/workspace/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
$instrumentationTarget = "com.projectz.cannyminute.test/androidx.test.runner.AndroidJUnitRunner"

function Stop-Emulator {
    param([string[]]$ComposeArgs)
    docker compose @ComposeArgs down --remove-orphans | Out-Host
}

function Test-ContainerRunning {
    param([string]$ContainerName)
    $id = docker ps -q -f "name=^$ContainerName$"
    return $LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($id)
}

function Print-EmulatorLogs {
    param([string]$ContainerName)
    Write-Host "[androidTest] Emulator logs (tail):"
    docker logs --tail 200 $ContainerName | Out-Host
}

Push-Location $repoRoot

try {
    Write-Host "[androidTest] Building docker image (cannyminute/android-test:local)..."
    docker compose -f docker-compose.android.yml build emulator
    if ($LASTEXITCODE -ne 0) { throw "Docker image build failed." }

    Write-Host "[androidTest] Building app + androidTest APKs..."
    docker run --rm `
        --user 0 `
        -v "${repoRoot}:/workspace" `
        -w /workspace `
        --entrypoint /bin/bash `
        cannyminute/android-test:local `
        -lc "./gradlew --no-daemon --stacktrace assembleDebug assembleDebugAndroidTest"
    if ($LASTEXITCODE -ne 0) { throw "APK build failed." }

    Write-Host "[androidTest] Starting emulator (KVM required)..."
    docker compose @activeComposeArgs up -d emulator | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Failed to start emulator container with KVM." }

    Write-Host "[androidTest] Waiting for emulator readiness..."
    $ready = $false
    for ($i = 0; $i -lt 120; $i++) {
        if (-not (Test-ContainerRunning -ContainerName $containerName)) {
            $logs = docker logs $containerName 2>&1
            if ($logs -match "/dev/kvm cannot be found!") {
                throw "/dev/kvm is not available to Docker. Enable nested virtualization / KVM passthrough and retry."
            }
            throw "Emulator container stopped unexpectedly."
        }
        $status = docker exec $containerName bash -lc "cat /home/androidusr/device_status 2>/dev/null || cat device_status 2>/dev/null || true"
        $adbState = docker exec $containerName bash -lc "adb get-state 2>/dev/null || true"
        $bootCompleted = docker exec $containerName bash -lc "adb shell getprop sys.boot_completed 2>/dev/null || true"
        $packageService = docker exec $containerName bash -lc "adb shell service check package 2>/dev/null || true"
        $statusText = if ($null -ne $status) { $status.Trim() } else { "" }
        $adbStateText = if ($null -ne $adbState) { $adbState.Trim() } else { "" }
        $bootCompletedText = if ($null -ne $bootCompleted) { $bootCompleted.Trim() } else { "" }
        $packageServiceText = if ($null -ne $packageService) { $packageService.Trim() } else { "" }
        if ((($LASTEXITCODE -eq 0 -and $statusText -in @("device", "READY")) -or $adbStateText -eq "device") `
            -and $bootCompletedText -eq "1" `
            -and $packageServiceText -like "*found*") {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 5
    }

    if (-not $ready) {
        Print-EmulatorLogs -ContainerName $containerName
        throw "Emulator did not become ready in time."
    }

    Write-Host "[androidTest] Installing APKs..."
    docker exec $containerName adb install -r $appApkPath | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Failed to install app APK." }

    docker exec $containerName adb install -r $testApkPath | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Failed to install androidTest APK." }

    Write-Host "[androidTest] Running instrumentation via adb..."
    docker exec $containerName adb shell am instrument -w $instrumentationTarget
    if ($LASTEXITCODE -ne 0) { throw "Instrumentation tests failed." }

    Write-Host "[androidTest] Instrumentation tests completed."
}
finally {
    if (-not $KeepEmulator) {
        Write-Host "[androidTest] Stopping emulator container..."
        Stop-Emulator -ComposeArgs $activeComposeArgs
    }
    else {
        Write-Host "[androidTest] KeepEmulator set. Leaving emulator running."
    }
    Pop-Location
}
