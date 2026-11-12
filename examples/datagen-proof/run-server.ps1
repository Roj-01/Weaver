$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$logs = Join-Path $out "logs"
$launcher = Join-Path $repo "tools\launcher\launch.ps1"
$agent = Join-Path $repo "core\build\agent\weaver-agent.jar"
$serverDir = if ($env:WEAVER_SERVER_DIR) { $env:WEAVER_SERVER_DIR } else { Join-Path $repo "work\server-26.3" }
$port = if ($env:WEAVER_SERVER_PORT) { $env:WEAVER_SERVER_PORT } else { "25567" }
$log = Join-Path $logs "server.log"
$markers = Join-Path $logs "server-markers.log"

if (-not (Test-Path (Join-Path $out "datagen-proof.jar"))) { throw "run build.ps1 first" }
if (-not (Test-Path (Join-Path $out "server-pack"))) { throw "run build.ps1 first (server-pack missing)" }

$packSource = Join-Path $out "server-pack"
$packDest = Join-Path $serverDir "weaver-test\datapacks\datagen-proof"
if (Test-Path $packDest) { Remove-Item -LiteralPath $packDest -Recurse -Force }
New-Item -ItemType Directory -Path (Split-Path -Parent $packDest) -Force | Out-Null
Copy-Item -LiteralPath $packSource -Destination $packDest -Recurse -Force

New-Item -ItemType Directory -Path $logs -Force | Out-Null

$arguments = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $launcher,
    "-Side", "server",
    "-ServerDir", $serverDir,
    "-AgentJar", $agent,
    "-ModsDir", (Join-Path $out "mods"),
    "-ConfigDir", (Join-Path $out "server-config"),
    "-DataDir", (Join-Path $out "server-data"),
    "-Log", $log,
    "-Markers", $markers,
    "-Port", $port,
    "-TimeoutSeconds", "180",
    "-WaitFor", "DATAGEN PROOF ok=true"
)

Write-Host "SERVER START dir=$serverDir port=$port pack=$packDest log=$log"
try {
    & powershell @arguments
    $code = $LASTEXITCODE
    if ($code -ne 0) {
        Write-Host "SERVER RESULT ok=false log=$log"
        if (Test-Path $log) { Get-Content -LiteralPath $log -Tail 80 }
        exit $code
    }
    Write-Host "SERVER RESULT ok=true log=$log"
    Select-String -LiteralPath $log -Pattern "DATAGEN PROOF" |
        ForEach-Object { $_.Line }
} finally {
    if (Test-Path $packDest) { Remove-Item -LiteralPath $packDest -Recurse -Force }
}
