$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$logs = Join-Path $out "logs"
$launcher = Join-Path $repo "tools\launcher\launch.ps1"
$agent = Join-Path $repo "core\build\agent\weaver-agent.jar"
$serverDir = if ($env:WEAVER_SERVER_DIR) { $env:WEAVER_SERVER_DIR } else { Join-Path $repo "work\server-26.3" }
$port = if ($env:WEAVER_SERVER_PORT) { $env:WEAVER_SERVER_PORT } else { "25566" }
$log = Join-Path $logs "server.log"
$markers = Join-Path $logs "server-markers.log"

if (-not (Test-Path (Join-Path $out "proof.jar"))) { throw "run build.ps1 first" }
New-Item -ItemType Directory -Path $logs -Force | Out-Null

# The data registry check reads the proof data pack through the server data pack load.
$levelName = "world"
$properties = Join-Path $serverDir "server.properties"
if (Test-Path $properties) {
    $line = Select-String -LiteralPath $properties -Pattern "^level-name=(.+)$" | Select-Object -First 1
    if ($line) { $levelName = $line.Matches[0].Groups[1].Value.Trim() }
}
$datapacks = Join-Path (Join-Path $serverDir $levelName) "datapacks"
New-Item -ItemType Directory -Path $datapacks -Force | Out-Null
$proofPack = Join-Path $datapacks "weaverproof_data"
if (Test-Path $proofPack) { Remove-Item -LiteralPath $proofPack -Recurse -Force }
Copy-Item -LiteralPath (Join-Path (Join-Path $root "datapack") "weaverproof_data") -Destination $proofPack -Recurse

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
    "-WaitFor", "PROOF RESULT ok=true side=server"
)

Write-Host "SERVER START dir=$serverDir port=$port log=$log"
& powershell @arguments
if ($LASTEXITCODE -ne 0) {
    Write-Host "SERVER RESULT ok=false log=$log"
    if (Test-Path $log) { Get-Content -LiteralPath $log -Tail 80 }
    exit 1
}
Write-Host "SERVER RESULT ok=true log=$log"
Select-String -LiteralPath $log -Pattern "PROOF (PHASE|CONTENT|CHECK|RESULT|MIXIN)" |
    ForEach-Object { $_.Line }
