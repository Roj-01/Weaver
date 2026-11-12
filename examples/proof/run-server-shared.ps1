[CmdletBinding()]
param(
    [switch]$AllowDocumentedBlocker
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$logs = Join-Path $out "logs"
$launcher = Join-Path $repo "tools\launcher\launch.ps1"
$agent = Join-Path $repo "core\build\agent\weaver-agent.jar"
$agentLib = Join-Path $repo "core\build\agent\lib"
$serverDir = if ($env:WEAVER_SERVER_DIR) { $env:WEAVER_SERVER_DIR } else { Join-Path $repo "work\server-26.3" }
$port = if ($env:WEAVER_SERVER_PORT) { $env:WEAVER_SERVER_PORT } else { "25566" }
$log = Join-Path $logs "server-shared.log"
$markers = Join-Path $logs "server-shared-markers.log"
$proofJar = Join-Path $out "proof.jar"

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + $candidates + @("C:\Program Files\Java\jdk-26.0.1")
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "bin\java.exe")) { return $candidate }
    }
    throw "No JDK 26 found. Set JAVA_HOME."
}

function Get-ServerClasspath {
    $serverJar = Join-Path $serverDir "versions\26.3\server-26.3.jar"
    if (-not (Test-Path $serverJar)) { throw "missing real server jar: $serverJar" }
    $libraries = Get-ChildItem -LiteralPath (Join-Path $serverDir "libraries") -Recurse -Filter *.jar |
        ForEach-Object { $_.FullName }
    return (@($libraries) + @($serverJar)) -join ";"
}

function Get-LinkageLines {
    if (-not (Test-Path $log)) { return @() }
    return @(Select-String -LiteralPath $log -Pattern "LinkageError|NoClassDefFoundError" -ErrorAction SilentlyContinue)
}

function Get-RelevantLines {
    if (-not (Test-Path $log)) { return @() }
    $matches = Select-String -LiteralPath $log -Pattern "\[weaver\] (CLASS SPACE|TYPED EVENTS|AGENT|GAME HOOK|MOD )|PROOF|LinkageError|NoClassDefFoundError|ClassNotFoundException" -ErrorAction SilentlyContinue
    if ($null -eq $matches) { return @() }
    return @($matches | ForEach-Object { $_.Line })
}

function Invoke-FactoryProbe {
    if (-not (Test-Path $agentLib)) {
        Write-Host "SHARED FACTORY PROBE ok=false reason=agent-lib-missing dir=$agentLib"
        return $false
    }
    $libCp = (Get-ChildItem -LiteralPath $agentLib -Filter *.jar |
        ForEach-Object { $_.FullName }) -join ";"
    $serverCp = Get-ServerClasspath
    $classes = Join-Path $out "classes"
    $probeCp = "$libCp;$classes;$serverCp"
    & $java "-cp" $probeCp "net.weaver.proof.SharedFactoryProbe" $proofJar
    $probeExit = $LASTEXITCODE
    if ($probeExit -ne 0) {
        Write-Host "SHARED FACTORY PROBE ok=false reason=exit=$probeExit"
        return $false
    }
    Write-Host "SHARED FACTORY PROBE ok=true"
    return $true
}

function Get-Blocker {
    param(
        [bool]$Shared,
        [bool]$Proof,
        [bool]$TypedEnabled,
        [int]$TypedUnavailable,
        [bool]$Init,
        [bool]$Hooks,
        [bool]$ProofFailure
    )
    if (-not $Shared) { return "missing-class-space-mode=shared-marker" }
    if (-not $TypedEnabled -and $TypedUnavailable -ne 1) {
        return "typed-events-not-enabled-unavailable=$TypedUnavailable"
    }
    if ($Proof) { return "" }
    if (-not $Init -and -not $Hooks) {
        return "game-classes-hosted-by-system-loader-shared-transforms-skipped-no-proof-init"
    }
    if (-not $Init) { return "mod-lifecycle-not-triggered-no-proof-init" }
    if ($ProofFailure) { return "proof-reported-ok-false-see-log" }
    return "no-proof-result-see-log"
}

if (-not (Test-Path $proofJar)) {
    Write-Host "SHARED BUILD missing proof.jar; running build.ps1"
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root "build.ps1")
    if ($LASTEXITCODE -ne 0) { throw "proof build failed" }
}

New-Item -ItemType Directory -Path $logs -Force | Out-Null
$jdk = Find-Jdk
$java = Join-Path $jdk "bin\java.exe"

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
    "-WaitFor", "(?s)CLASS SPACE mode=shared.*PROOF RESULT ok=true side=server",
    "-SharedClassSpace"
)

Write-Host "SHARED SERVER START dir=$serverDir port=$port log=$log"
& powershell @arguments
$launcherExit = $LASTEXITCODE

$text = if (Test-Path $log) { Get-Content -LiteralPath $log -Raw } else { "" }
if ($null -eq $text) { $text = "" }

$shared = $text -match "\[weaver\] CLASS SPACE mode=shared"
$typedEnabled = $text -match "\[weaver\] TYPED EVENTS enabled"
$typedUnavailable = [regex]::Matches($text, "\[weaver\] TYPED EVENTS unavailable reason=").Count
$proof = $text -match "PROOF RESULT ok=true side=server"
$proofFailure = $text -match "PROOF RESULT ok=false"
$init = $text -match "PROOF (INIT|PHASE)"
$hooks = $text -match "GAME HOOK APPLIED"
$linkage = Get-LinkageLines
$probeOk = $false
$blocker = Get-Blocker -Shared $shared -Proof $proof -TypedEnabled $typedEnabled `
    -TypedUnavailable $typedUnavailable -Init $init -Hooks $hooks -ProofFailure $proofFailure

if ($proof) {
    Write-Host "SHARED MODE RESULT ok=true side=server log=$log"
    Select-String -LiteralPath $log -Pattern "PROOF (PHASE|CHECK|RESULT)" |
        ForEach-Object { $_.Line }
} else {
    Write-Host "SHARED MODE RESULT ok=false blocker=$blocker log=$log launcher_exit=$launcherExit"
    Get-RelevantLines | Select-Object -Last 60 | ForEach-Object { Write-Host $_ }
}

$probeOk = Invoke-FactoryProbe

if ($linkage.Count -gt 0) {
    Write-Host "SHARED MODE RESULT ok=false blocker=linkage-error log=$log"
    $linkage | ForEach-Object { Write-Host $_.Line }
    exit 1
}
if (-not $shared) {
    exit 1
}
if ($proof) {
    if (-not $typedEnabled -and $typedUnavailable -ne 1) {
        exit 1
    }
    if (-not $probeOk) {
        exit 1
    }
    exit 0
}
if ($blocker -and $probeOk) {
    if ($AllowDocumentedBlocker) {
        Write-Host "SHARED MODE ACKNOWLEDGED ok=false blocker=$blocker probe=ok"
        Write-Host "Shared class space is experimental and is not a passing proof gate."
        exit 0
    }
    Write-Host "SHARED MODE FAILED ok=false blocker=$blocker probe=ok"
    Write-Host "Shared class space did not pass. Rerun with -AllowDocumentedBlocker only to acknowledge the documented architectural blocker."
    exit 1
}
exit 1
