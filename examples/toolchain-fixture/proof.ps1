$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$launcher = Join-Path $repo "tools\launcher\launch.ps1"
$agentJar = Join-Path $repo "core\build\agent\weaver-agent.jar"
$serverDir = if ($env:WEAVER_SERVER_DIR) { $env:WEAVER_SERVER_DIR } else { Join-Path $repo "work\server-26.3" }
$modsDir = Join-Path $root "build\weaver\mods"
$logsDir = Join-Path $root "build\weaver\logs"
$log = Join-Path $logsDir "server.log"
$markers = Join-Path $logsDir "server-markers.log"

if (-not (Test-Path -LiteralPath $launcher)) { throw "launcher is missing: $launcher" }
if (-not (Test-Path -LiteralPath $agentJar)) { throw "agent jar is missing: $agentJar (run build.ps1 first)" }
if (-not (Test-Path -LiteralPath $modsDir)) { throw "run mods directory is missing: $modsDir (run build.ps1 first)" }

New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

$arguments = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $launcher,
    "-Side", "server",
    "-ServerDir", $serverDir,
    "-AgentJar", $agentJar,
    "-ModsDir", $modsDir,
    "-ConfigDir", (Join-Path $root "build\weaver\server-config"),
    "-DataDir", (Join-Path $root "build\weaver\server-data"),
    "-Log", $log,
    "-Markers", $markers,
    "-Port", "25567",
    "-TimeoutSeconds", "180",
    "-WaitFor", "TOOLCHAINFIXTURE RESULT ok=true side=server"
)

Write-Host "TOOLCHAINFIXTURE PROOF START dir=$serverDir port=25567 log=$log"
& powershell @arguments
if ($LASTEXITCODE -ne 0) {
    Write-Host "TOOLCHAINFIXTURE PROOF ok=false log=$log"
    if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Tail 80 }
    if (Test-Path -LiteralPath "$log.err") { Get-Content -LiteralPath "$log.err" -Tail 40 }
    exit 1
}

$resultInLog = (Test-Path -LiteralPath $log) -and
    (Select-String -LiteralPath $log -Pattern "TOOLCHAINFIXTURE RESULT ok=true side=server" -Quiet)
$resultInMarkers = (Test-Path -LiteralPath $markers) -and
    (Select-String -LiteralPath $markers -Pattern "TOOLCHAINFIXTURE RESULT ok=true side=server" -Quiet)
if (-not $resultInLog -and -not $resultInMarkers) {
    Write-Host "TOOLCHAINFIXTURE PROOF ok=false reason=result-marker-missing log=$log"
    if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Tail 80 }
    exit 1
}

Write-Host "TOOLCHAINFIXTURE PROOF ok=true"
if (Test-Path -LiteralPath $markers) {
    Select-String -LiteralPath $markers -Pattern "TOOLCHAINFIXTURE" |
        ForEach-Object { $_.Line }
}
if (Test-Path -LiteralPath $log) {
    Select-String -LiteralPath $log -Pattern "TOOLCHAINFIXTURE (main|nested|RESULT)" |
        ForEach-Object { $_.Line }
}
exit 0
