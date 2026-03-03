$ErrorActionPreference = "Stop"

$scriptRoot = $PSScriptRoot

& "$scriptRoot/run-unit-tests.ps1"
& "$scriptRoot/run-instrumented-tests.ps1"

