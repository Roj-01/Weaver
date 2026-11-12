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

if (-not (Test-Path (Join-Path $out "client-proof.jar"))) { throw "run build.ps1 first" }
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
    "-WaitFor", "CLIENT PROOF SERVER RESULT ok=true side=server"
)

Write-Host "CLIENT PROOF SERVER START dir=$serverDir port=$port log=$log"
& powershell @arguments
if ($LASTEXITCODE -ne 0) {
    Write-Host "CLIENT PROOF SERVER RESULT ok=false log=$log"
    if (Test-Path $log) { Get-Content -LiteralPath $log -Tail 80 }
    exit 1
}
$text = Get-Content -LiteralPath $log -Raw
$clientApi = $text -match "client_api=false"
$hud = $text -match "CLIENT PROOF HUD"
$screen = $text -match "CLIENT PROOF SCREEN"
$keybind = $text -match "CLIENT PROOF KEYBIND"
$hand = $text -match "CLIENT PROOF HAND RENDER"
$fog = $text -match "CLIENT PROOF FOG"
$blockColor = $text -match "CLIENT PROOF BLOCK COLOR"
if (-not $clientApi -or $hud -or $screen -or $keybind -or $hand -or $fog -or $blockColor) {
    Write-Host "CLIENT PROOF SERVER SAFETY ok=false client_api=$clientApi hud=$hud screen=$screen keybind=$keybind hand=$hand fog=$fog block_color=$blockColor log=$log"
    Select-String -LiteralPath $log -Pattern "CLIENT PROOF" |
        ForEach-Object { $_.Line }
    exit 1
}
Write-Host "CLIENT PROOF SERVER SAFETY ok=true client_api=false hud=false screen=false keybind=false hand=false fog=false block_color=false"
Write-Host "CLIENT PROOF SERVER RESULT ok=true log=$log"
Select-String -LiteralPath $log -Pattern "CLIENT PROOF" |
    ForEach-Object { $_.Line }
