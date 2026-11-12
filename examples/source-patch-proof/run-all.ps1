$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path

& (Join-Path $root "build.ps1")
if ($LASTEXITCODE -ne 0) { throw "source patch proof build failed" }

& (Join-Path $root "run-server.ps1")
if ($LASTEXITCODE -ne 0) { throw "source patch server proof failed" }

& (Join-Path $root "run-client.ps1")
if ($LASTEXITCODE -ne 0) { throw "source patch client proof failed" }

Write-Host "SOURCE PATCH PROOF RUN ok=true server=ok client=ok"
