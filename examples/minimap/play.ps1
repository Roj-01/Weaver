#requires -Version 5.1
param(
    [string]$Proof = $env:WEAVER_MINIMAP_PROOF,
    [string]$ProofDir = ""
)
$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$gameDir = if ($env:WEAVER_GAME_DIR) { $env:WEAVER_GAME_DIR } else { Join-Path $repo "work\game-26.3" }
$fixture = Join-Path $repo "examples\proof\fixture-world"
$fixtureName = "WeaverProofFixture"
$launcher = Join-Path $repo "tools\launcher\launch.ps1"
$agent = Join-Path $repo "core\build\agent\weaver-agent.jar"
$mods = Join-Path $PSScriptRoot "build\weaver\mods"
$config = Join-Path $PSScriptRoot "build\weaver\config"
$data = Join-Path $PSScriptRoot "build\weaver\data"
$modJar = Join-Path $mods "Weaver Minimap.jar"
$logs = Join-Path $PSScriptRoot "build\weaver\logs"
New-Item -ItemType Directory -Path $logs -Force | Out-Null
$logFile = Join-Path $logs "play.log"
$markersFile = Join-Path $logs "play-markers.log"

if (-not (Test-Path -LiteralPath $agent -PathType Leaf)) {
    throw "Agent jar missing: $agent. Run .\gradlew.bat :core:agentJar from the repository root."
}
if (-not (Test-Path -LiteralPath $modJar -PathType Leaf)) {
    throw "Minimap jar missing: $modJar. Run .\gradlew.bat weaverStage from examples\minimap."
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

if ($Proof) {
    if (-not $ProofDir) {
        $ProofDir = Join-Path $PSScriptRoot "build\screenshots"
    }
    New-Item -ItemType Directory -Path $ProofDir -Force | Out-Null
    $ProofDir = [System.IO.Path]::GetFullPath($ProofDir)
    Remove-Item -LiteralPath (Join-Path $config "weaver-minimap") -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $data -Recurse -Force -ErrorAction SilentlyContinue
    $env:JAVA_TOOL_OPTIONS = "-Dweaver.minimap.proof=$Proof -Dweaver.minimap.proofDir=$ProofDir"
}

Write-Host "WEAVER MINIMAP starting"
Write-Host "  game:  $gameDir"
Write-Host "  world: $fixtureName"
Write-Host "  mod:   $modJar"
if ($Proof) {
    Write-Host "  proof: $Proof -> $ProofDir"
}
Write-Host "Close the Minecraft window to stop."
Write-Host ""

& powershell -NoProfile -ExecutionPolicy Bypass -File $launcher `
    -Side client `
    -GameDir $gameDir `
    -AgentJar $agent `
    -ModsDir $mods `
    -ConfigDir $config `
    -DataDir $data `
    -Log $logFile `
    -Markers $markersFile `
    -Username WeaverMinimap `
    -QuickPlayWorld $fixtureName `
    -IntegratedServer `
    -Interactive
