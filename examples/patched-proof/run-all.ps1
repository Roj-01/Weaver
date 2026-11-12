$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path

& (Join-Path $root "build.ps1")
if ($LASTEXITCODE -ne 0) { throw "patched proof build failed" }

& (Join-Path $root "run-server.ps1")
if ($LASTEXITCODE -ne 0) { throw "patched server proof failed" }

& (Join-Path $root "run-client.ps1")
if ($LASTEXITCODE -ne 0) { throw "patched client proof failed" }

Write-Host "PATCHED PROOF RUN ok=true server=ok client=ok"
