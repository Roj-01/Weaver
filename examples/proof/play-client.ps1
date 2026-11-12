$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$gameDir = if ($env:WEAVER_GAME_DIR) { $env:WEAVER_GAME_DIR } else { Join-Path $repo "work\game-26.3" }
$fixture = Join-Path $root "fixture-world"
$fixtureName = "WeaverProofFixture"
$out = Join-Path $root "out"
$launcher = Join-Path $repo "tools\launcher\launch.ps1"
$agent = Join-Path $repo "core\build\agent\weaver-agent.jar"
$mods = Join-Path $out "mods"
$config = Join-Path $out "client-config"
$data = Join-Path $out "client-data"

if (-not (Test-Path -LiteralPath $agent -PathType Leaf)) {
    throw "Agent jar missing: $agent. Run .\gradlew.bat :core:agentJar first."
}
if (-not (Test-Path -LiteralPath (Join-Path $mods "proof.jar") -PathType Leaf)) {
    throw "Proof mod missing: $mods\proof.jar. Run .\build.ps1 first."
}
if (-not (Test-Path -LiteralPath (Join-Path $gameDir "classpath.txt") -PathType Leaf)) {
    throw "Game classpath missing: $gameDir\classpath.txt. Set WEAVER_GAME_DIR."
}

$target = Join-Path $gameDir "saves\$fixtureName"
if (Test-Path -LiteralPath $target) {
    Remove-Item -LiteralPath $target -Recurse -Force
}
New-Item -ItemType Directory -Path $target -Force | Out-Null
if (Test-Path -LiteralPath (Join-Path $fixture "level.dat") -PathType Leaf) {
    Get-ChildItem -LiteralPath $fixture -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $target -Recurse -Force
    }
}
Remove-Item -LiteralPath (Join-Path $target "session.lock") -Force -ErrorAction SilentlyContinue

Write-Host "WEAVER PLAY starting"
Write-Host "  game:   $gameDir"
Write-Host "  world:  $fixtureName"
Write-Host "  mods:   $mods"
Write-Host "Close the Minecraft window to stop."
Write-Host ""

try {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $launcher `
        -Side client `
        -GameDir $gameDir `
        -AgentJar $agent `
        -ModsDir $mods `
        -ConfigDir $config `
        -DataDir $data `
        -Username WeaverProof `
        -QuickPlayWorld $fixtureName `
        -IntegratedServer `
        -Interactive
} catch {
    Write-Host ""
    Write-Host "WEAVER PLAY FAILED: $($_.Exception.Message)" -ForegroundColor Red
    Read-Host "Press Enter to close"
    exit 1
}
