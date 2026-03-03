param(
    [switch]$SkipImageBuild
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path "$PSScriptRoot/../..").Path
Push-Location $repoRoot

try {
    if (-not $SkipImageBuild) {
        Write-Host "[unit] Building docker image (cannyminute/android-test:local)..."
        docker compose -f docker-compose.android.yml build emulator
        if ($LASTEXITCODE -ne 0) { throw "Docker image build failed." }
    }

    Write-Host "[unit] Running testDebugUnitTest inside docker image..."
    docker run --rm `
        --user 0 `
        -v "${repoRoot}:/workspace" `
        -w /workspace `
        --entrypoint /bin/bash `
        cannyminute/android-test:local `
        -lc "./gradlew --no-daemon --stacktrace testDebugUnitTest"
    if ($LASTEXITCODE -ne 0) { throw "Unit tests failed." }

    Write-Host "[unit] Unit tests completed."
}
finally {
    Pop-Location
}
