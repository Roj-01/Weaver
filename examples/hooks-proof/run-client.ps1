$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$logs = Join-Path $out "logs"
$launcher = Join-Path $repo "tools\launcher\launch.ps1"
$agent = Join-Path $repo "core\build\agent\weaver-agent.jar"
$gameDir = if ($env:WEAVER_GAME_DIR) { $env:WEAVER_GAME_DIR } else { Join-Path $repo "work\game-26.3" }
$fixture = Join-Path (Split-Path -Parent $root) "proof\fixture-world"
$fixtureName = "WeaverHooksProofFixture"
$worldOverride = $env:WEAVER_CLIENT_WORLD
if (-not $worldOverride -and (Test-Path -LiteralPath (Join-Path $fixture "level.dat") -PathType Leaf)) {
    $saves = Join-Path $gameDir "saves"
    $target = Join-Path $saves $fixtureName
    if (Test-Path -LiteralPath $target) {
        Remove-Item -LiteralPath $target -Recurse -Force
    }
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    Get-ChildItem -LiteralPath $fixture -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $target -Recurse -Force
    }
    Remove-Item -LiteralPath (Join-Path $target "session.lock") -Force -ErrorAction SilentlyContinue
    $world = $fixtureName
    Write-Host "HOOKS CLIENT FIXTURE world=$world source=$fixture"
} else {
    $world = if ($worldOverride) { $worldOverride } else { "New World (2)" }
    if (-not (Test-Path -LiteralPath (Join-Path $fixture "level.dat") -PathType Leaf)) {
        Write-Host "HOOKS CLIENT FIXTURE missing=$fixture; using world=$world"
    }
}
$log = Join-Path $logs "client.log"
$markers = Join-Path $logs "client-markers.log"

if (-not (Test-Path (Join-Path $out "hooks-proof.jar"))) { throw "run build.ps1 first" }
New-Item -ItemType Directory -Path $logs -Force | Out-Null

$arguments = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $launcher,
    "-Side", "client",
    "-GameDir", $gameDir,
    "-AgentJar", $agent,
    "-ModsDir", (Join-Path $out "mods"),
    "-ConfigDir", (Join-Path $out "client-config"),
    "-DataDir", (Join-Path $out "client-data"),
    "-Log", $log,
    "-Markers", $markers,
    "-Username", "WeaverHooksProof",
    "-QuickPlayWorld", $world,
    "-IntegratedServer",
    "-TimeoutSeconds", "300",
    "-WaitFor", "PROOF RESULT ok=true side=client"
)

Write-Host "HOOKS CLIENT START game=$gameDir world=$world log=$log"
& powershell @arguments
if ($LASTEXITCODE -ne 0) {
    Write-Host "HOOKS CLIENT RESULT ok=false log=$log"
    if (Test-Path $log) { Get-Content -LiteralPath $log -Tail 100 }
    exit 1
}
Write-Host "HOOKS CLIENT RESULT ok=true log=$log markers=$markers"
Select-String -LiteralPath $log -Pattern "PROOF (CHECK|EVENT|RESULT)" |
    ForEach-Object { $_.Line }
