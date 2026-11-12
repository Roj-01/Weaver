$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path

& (Join-Path $root "build.ps1")
if ($LASTEXITCODE -ne 0) { throw "hooks proof build failed" }

& (Join-Path $root "run-server.ps1")
if ($LASTEXITCODE -ne 0) { throw "server hooks proof failed" }

& (Join-Path $root "run-client.ps1")
if ($LASTEXITCODE -ne 0) { throw "client hooks proof failed" }

Write-Host "HOOKS PROOF RESULT server=ok client=ok"
