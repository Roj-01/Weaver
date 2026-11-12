param(
    [Parameter(Mandatory = $true)]
    [string]$GameDir,
    [string]$Mods = "",
    [string]$AgentJar = "",
    [string]$Target = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $AgentJar) { $AgentJar = Join-Path $root "core\build\agent\weaver-agent.jar" }
if (-not $Mods) { $Mods = Join-Path $root "examples\proof\out\mods" }
if (-not $Target) { $Target = Join-Path $GameDir "weaver" }

if (-not (Test-Path $AgentJar)) { throw "agent jar missing: $AgentJar" }
$lib = Join-Path (Split-Path -Parent $AgentJar) "lib"
if (-not (Test-Path $lib)) { throw "agent lib missing: $lib" }
New-Item -ItemType Directory -Path (Join-Path $Target "agent\lib") -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $Target "mods") -Force | Out-Null

Copy-Item -LiteralPath $AgentJar -Destination (Join-Path $Target "agent\weaver-agent.jar") -Force
Copy-Item -Path (Join-Path $lib "*") -Destination (Join-Path $Target "agent\lib") -Recurse -Force

$installedGameApi = @(Get-ChildItem -Path (Join-Path $Target "agent\lib\api-game-*.jar") -File -ErrorAction SilentlyContinue)
if ($installedGameApi.Count -ne 1) {
    throw "agent lib must contain exactly one api-game-*.jar (found $($installedGameApi.Count) in $Target\agent\lib); run .\gradlew.bat :core:agentJar"
}

if (Test-Path $Mods) {
    Copy-Item -Path (Join-Path $Mods "*") -Destination (Join-Path $Target "mods") -Recurse -Force
}

$launcher = Join-Path $root "tools\launcher\launch.ps1"
Write-Host "INSTALL ok=true target=$Target agent=$Target\agent\weaver-agent.jar gameApi=$($installedGameApi[0].Name) mods=$Target\mods"
Write-Host "LAUNCH client: powershell -File $launcher -Side client -GameDir $GameDir -AgentJar $Target\agent\weaver-agent.jar -ModsDir $Target\mods -Interactive"
Write-Host "LAUNCH server: powershell -File $launcher -Side server -ServerDir <server-dir> -AgentJar $Target\agent\weaver-agent.jar -ModsDir $Target\mods -Interactive"
