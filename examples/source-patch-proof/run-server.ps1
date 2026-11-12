$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$logs = Join-Path $out "logs"
$launcher = Join-Path $repo "tools\launcher\launch.ps1"
$agent = Join-Path $repo "core\build\agent\weaver-agent.jar"
$serverDir = if ($env:WEAVER_SERVER_DIR) { $env:WEAVER_SERVER_DIR } else { Join-Path $repo "work\server-26.3" }
$port = if ($env:WEAVER_SERVER_PORT) { $env:WEAVER_SERVER_PORT } else { "25568" }
$log = Join-Path $logs "server.log"
$markers = Join-Path $logs "server-markers.log"
$modJar = Join-Path $out "source-patch-proof.jar"
$modsJar = Join-Path $out "mods\source-patch-proof.jar"
$patchedServer = Join-Path $out "source-patched\server-source-patched.jar"

if (-not (Test-Path -LiteralPath $modJar -PathType Leaf)) {
    throw "run build.ps1 first: missing $modJar"
}
if (-not (Test-Path -LiteralPath $modsJar -PathType Leaf)) {
    throw "run build.ps1 first: missing $modsJar"
}
if (-not (Test-Path -LiteralPath $patchedServer -PathType Leaf)) {
    throw "missing source-patched server jar: $patchedServer (run build.ps1)"
}
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw "launcher missing: $launcher"
}
if (-not (Select-String -LiteralPath $launcher -Pattern "PatchedServerJar" -Quiet)) {
    throw "launcher does not expose -PatchedServerJar yet: $launcher"
}
if (-not (Test-Path -LiteralPath $agent -PathType Leaf)) {
    throw "agent jar missing: $agent (run .\gradlew.bat :core:agentJar)"
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
    "-WaitFor", "SOURCE PATCH PROOF ok=true side=server",
    "-PatchedServerJar", $patchedServer
)

Write-Host "SOURCE PATCH PROOF SERVER START dir=$serverDir port=$port jar=$patchedServer log=$log"
& powershell @arguments
if ($LASTEXITCODE -ne 0) {
    Write-Host "SOURCE PATCH PROOF SERVER ok=false"
    Write-Host "SOURCE PATCH PROOF SERVER REASON reason=launcher-exit log=$log"
    if (Test-Path $log) { Get-Content -LiteralPath $log -Tail 80 }
    exit 1
}
$text = Get-Content -LiteralPath $log -Raw
if ($text -match "SOURCE PATCH PROOF ok=false side=server") {
    Write-Host "SOURCE PATCH PROOF SERVER ok=false"
    Write-Host "SOURCE PATCH PROOF SERVER REASON reason=failure-marker-present log=$log"
    Select-String -LiteralPath $log -Pattern "SOURCE PATCH PROOF" |
        ForEach-Object { $_.Line }
    exit 1
}
if ($text -notmatch "SOURCE PATCH PROOF ok=true side=server") {
    Write-Host "SOURCE PATCH PROOF SERVER ok=false"
    Write-Host "SOURCE PATCH PROOF SERVER REASON reason=missing-success-marker log=$log"
    Select-String -LiteralPath $log -Pattern "SOURCE PATCH PROOF" |
        ForEach-Object { $_.Line }
    exit 1
}
Write-Host "SOURCE PATCH PROOF SERVER ok=true"
Write-Host "SOURCE PATCH PROOF SERVER LOG log=$log markers=$markers"
Select-String -LiteralPath $log -Pattern "SOURCE PATCH PROOF" |
    ForEach-Object { $_.Line }
