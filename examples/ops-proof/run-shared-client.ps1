$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$proofRoot = Join-Path $repo "examples\proof"
$out = Join-Path $proofRoot "out"
$logs = Join-Path $out "logs"
$launcher = Join-Path $repo "tools\launcher\launch.ps1"
$agent = Join-Path $repo "core\build\agent\weaver-agent.jar"
$gameDir = if ($env:WEAVER_GAME_DIR) { $env:WEAVER_GAME_DIR } else { Join-Path $repo "work\game-26.3" }
$fixture = Join-Path $proofRoot "fixture-world"
$fixtureName = "WeaverProofFixture"
$log = Join-Path $logs "client-shared.log"
$markers = Join-Path $logs "client-shared-markers.log"

if (-not (Test-Path -LiteralPath (Join-Path $out "proof.jar"))) {
    throw "proof jar missing: run examples\proof\build.ps1 first"
}
if (-not (Test-Path -LiteralPath (Join-Path $fixture "level.dat") -PathType Leaf)) {
    throw "fixture world missing: $fixture"
}
New-Item -ItemType Directory -Path $logs -Force | Out-Null

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

Write-Host "OPS SHARED CLIENT START game=$gameDir world=$fixtureName log=$log"
& powershell -ExecutionPolicy Bypass -File $launcher `
    -Side client `
    -GameDir $gameDir `
    -AgentJar $agent `
    -ModsDir (Join-Path $out "mods") `
    -ConfigDir (Join-Path $out "client-shared-config") `
    -DataDir (Join-Path $out "client-shared-data") `
    -Log $log `
    -Markers $markers `
    -Username "WeaverProof" `
    -QuickPlayWorld $fixtureName `
    -IntegratedServer `
    -TimeoutSeconds 300 `
    -WaitFor "PROOF RESULT ok=true side=client" `
    -SharedClassSpace
if ($LASTEXITCODE -ne 0) {
    Write-Host "OPS SHARED CLIENT RESULT ok=false log=$log"
    if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Tail 100 }
    exit 1
}

$text = Get-Content -LiteralPath $log -Raw
if ($text -notmatch "\[weaver\] CLASS SPACE mode=shared") {
    Write-Host "OPS SHARED CLIENT RESULT ok=false reason=missing-shared-marker log=$log"
    exit 1
}
if ($text -notmatch "PROOF RESULT ok=true side=client") {
    Write-Host "OPS SHARED CLIENT RESULT ok=false reason=missing-proof-result log=$log"
    exit 1
}
Write-Host "OPS SHARED CLIENT RESULT ok=true log=$log"
Select-String -LiteralPath $log -Pattern "CLASS SPACE|SHARED MAIN|PROOF (PHASE|RESULT)" |
    ForEach-Object { $_.Line }
