[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$runner = Join-Path $root "run.ps1"

if (-not (Test-Path $runner)) {
    Write-Host "OPS SHARED PROOF ok=false reason=missing-run-script path=$runner"
    exit 1
}

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $runner -Shared
exit $LASTEXITCODE
