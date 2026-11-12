$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$logs = Join-Path $out "logs"
$launcher = Join-Path $repo "tools\launcher\launch.ps1"
$agent = Join-Path $repo "core\build\agent\weaver-agent.jar"
$serverDir = if ($env:WEAVER_SERVER_DIR) { $env:WEAVER_SERVER_DIR } else { Join-Path $repo "work\server-26.3" }
$port = if ($env:WEAVER_COMPAT_SUITE_PORT) { $env:WEAVER_COMPAT_SUITE_PORT } else { "25568" }
$log = Join-Path $logs "server.log"
$markers = Join-Path $logs "server-markers.log"

function Show-Tail([string]$path, [int]$lines = 80) {
    if (Test-Path -LiteralPath $path) {
        Write-Host "----- tail: $path -----"
        Get-Content -LiteralPath $path -Tail $lines
    }
}

if (-not (Test-Path (Join-Path $out "mods\compat-suite.jar"))) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root "build.ps1")
    if ($LASTEXITCODE -ne 0) {
        Write-Host "COMPAT SUITE PROOF ok=false reason=build-failed"
        exit 1
    }
}

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
    "-WaitFor", "COMPAT SUITE RESULT ok=(true|false)"
)

Write-Host "COMPAT SUITE START dir=$serverDir port=$port log=$log"
& powershell @arguments
$launcherExit = $LASTEXITCODE

$successPattern = "COMPAT SUITE RESULT ok=true side=server"
$resultInLog = (Test-Path -LiteralPath $log) -and
    (Select-String -LiteralPath $log -Pattern $successPattern -Quiet)
$resultInMarkers = (Test-Path -LiteralPath $markers) -and
    (Select-String -LiteralPath $markers -Pattern $successPattern -Quiet)

if ($launcherExit -ne 0 -or (-not $resultInLog -and -not $resultInMarkers)) {
    Write-Host "COMPAT SUITE PROOF ok=false log=$log"
    Show-Tail $log
    Show-Tail "$log.err" 40
    exit 1
}

Write-Host "COMPAT SUITE PROOF ok=true"
Select-String -LiteralPath $log -Pattern "COMPAT SUITE (CHECK|RESULT)" |
    ForEach-Object { $_.Line }
exit 0
