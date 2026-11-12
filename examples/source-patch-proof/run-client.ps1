$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$logs = Join-Path $out "logs"
$launcher = Join-Path $repo "tools\launcher\launch.ps1"
$agent = Join-Path $repo "core\build\agent\weaver-agent.jar"
$gameDir = if ($env:WEAVER_GAME_DIR) { $env:WEAVER_GAME_DIR } else { Join-Path $repo "work\game-26.3" }
$fixture = Join-Path (Split-Path -Parent $root) "proof\fixture-world"
$fixtureName = "WeaverSourcePatchProofFixture"
$modJar = Join-Path $out "source-patch-proof.jar"
$modsJar = Join-Path $out "mods\source-patch-proof.jar"
$patchedClient = Join-Path $out "source-patched\client-source-patched.jar"
$log = Join-Path $logs "client.log"
$markers = Join-Path $logs "client-markers.log"
$attempts = 3

if (-not (Test-Path -LiteralPath $modJar -PathType Leaf)) {
    throw "run build.ps1 first: missing $modJar"
}
if (-not (Test-Path -LiteralPath $modsJar -PathType Leaf)) {
    throw "run build.ps1 first: missing $modsJar"
}
if (-not (Test-Path -LiteralPath $patchedClient -PathType Leaf)) {
    throw "missing source-patched client jar: $patchedClient (run build.ps1)"
}
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw "launcher missing: $launcher"
}
if (-not (Select-String -LiteralPath $launcher -Pattern "PatchedClientJar" -Quiet)) {
    throw "launcher does not expose -PatchedClientJar yet: $launcher"
}
if (-not (Test-Path -LiteralPath $agent -PathType Leaf)) {
    throw "agent jar missing: $agent (run .\gradlew.bat :core:agentJar)"
}

$worldOverride = $env:WEAVER_CLIENT_WORLD
$useFixture = -not $worldOverride -and
    (Test-Path -LiteralPath (Join-Path $fixture "level.dat") -PathType Leaf)

function Prepare-World {
    if (-not $useFixture) {
        return
    }
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
}

if ($useFixture) {
    $world = $fixtureName
    Write-Host "SOURCE PATCH PROOF CLIENT FIXTURE world=$world source=$fixture"
} else {
    $world = if ($worldOverride) { $worldOverride } else { "New World (2)" }
    if (-not (Test-Path -LiteralPath (Join-Path $fixture "level.dat") -PathType Leaf)) {
        Write-Host "SOURCE PATCH PROOF CLIENT FIXTURE missing=$fixture; using world=$world"
    }
}

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
    "-Username", "WeaverSource",
    "-QuickPlayWorld", $world,
    "-IntegratedServer",
    "-TimeoutSeconds", "300",
    "-WaitFor", "SOURCE PATCH PROOF ok=true side=client",
    "-PatchedClientJar", $patchedClient
)

function Invoke-Attempt {
    param([Parameter(Mandatory = $true)][int]$Number)
    $useC1 = $Number -gt 1
    if ($useC1) {
        Write-Host "SOURCE PATCH PROOF CLIENT RETRY attempt=$Number jit=c1 reason=host-c2-or-gpu-crash"
        $previousOptions = $env:JAVA_TOOL_OPTIONS
        $env:JAVA_TOOL_OPTIONS = "-XX:TieredStopAtLevel=1"
    }
    try {
        Prepare-World
        & powershell @arguments
        $exitCode = $LASTEXITCODE
    }
    finally {
        if ($useC1) {
            $env:JAVA_TOOL_OPTIONS = $previousOptions
        }
    }
    if ($exitCode -ne 0) {
        return $false
    }
    $text = Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue
    if (-not $text -or $text -match "SOURCE PATCH PROOF ok=false") {
        return $false
    }
    return $text -match "SOURCE PATCH PROOF ok=true side=client"
}

Write-Host "SOURCE PATCH PROOF CLIENT START game=$gameDir world=$world jar=$patchedClient log=$log"
$success = $false
for ($attempt = 1; $attempt -le $attempts; $attempt++) {
    Write-Host "SOURCE PATCH PROOF CLIENT ATTEMPT number=$attempt"
    if (Invoke-Attempt -Number $attempt) {
        $success = $true
        break
    }
}

if (-not $success) {
    Write-Host "SOURCE PATCH PROOF CLIENT ok=false"
    Write-Host "SOURCE PATCH PROOF CLIENT REASON reason=launcher-exit attempts=$attempts log=$log"
    if (Test-Path $log) { Get-Content -LiteralPath $log -Tail 100 }
    exit 1
}
Write-Host "SOURCE PATCH PROOF CLIENT ok=true"
Write-Host "SOURCE PATCH PROOF CLIENT LOG log=$log markers=$markers"
Select-String -LiteralPath $log -Pattern "SOURCE PATCH PROOF" |
    ForEach-Object { $_.Line }

exit 0
