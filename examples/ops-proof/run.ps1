[CmdletBinding()]
param(
    [switch]$Shared
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$agentLib = Join-Path $repo "core\build\agent\lib"
$agentJar = Join-Path $repo "core\build\agent\weaver-agent.jar"
$out = Join-Path $root "out"
$classes = Join-Path $out "classes"

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + $candidates + @("C:\Program Files\Java\jdk-26.0.1")
    foreach ($candidate in $candidates) {
        $javac = Join-Path $candidate "bin\javac.exe"
        $java = Join-Path $candidate "bin\java.exe"
        if ((Test-Path $javac) -and (Test-Path $java)) { return $candidate }
    }
    throw "No JDK 26 found. Set JAVA_HOME to a JDK 26 installation."
}

if ((-not (Test-Path $agentLib)) -or (-not (Test-Path $agentJar))) {
    Write-Host "OPS PROOF BUILD agent-artifacts-missing path=$agentLib"
    $gradlew = Join-Path $repo "gradlew.bat"
    if (-not (Test-Path $gradlew)) {
        Write-Host "OPS PROOF BUILD ok=false reason=missing-gradlew"
        exit 1
    }
    Push-Location $repo
    try {
        & $gradlew ":core:agentJar" --no-daemon
    } finally {
        Pop-Location
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Host "OPS PROOF BUILD ok=false reason=gradle-agentJar exit=$LASTEXITCODE"
        exit 1
    }
}

if ((-not (Test-Path $agentLib)) -or (-not (Test-Path $agentJar))) {
    Write-Host "OPS PROOF BUILD ok=false reason=agent-artifacts-still-missing path=$agentLib"
    exit 1
}

try {
    $jdk = Find-Jdk
} catch {
    Write-Host "OPS PROOF BUILD ok=false reason=$($_.Exception.Message)"
    exit 1
}

$javac = Join-Path $jdk "bin\javac.exe"
$java = Join-Path $jdk "bin\java.exe"
$libJars = @(Get-ChildItem -LiteralPath $agentLib -Filter *.jar -File |
    Sort-Object Name |
    ForEach-Object { $_.FullName })
if ($libJars.Count -eq 0) {
    Write-Host "OPS PROOF BUILD ok=false reason=empty-agent-lib path=$agentLib"
    exit 1
}
$compileCp = (@($agentJar) + $libJars) -join ";"

if (Test-Path $out) {
    Remove-Item -LiteralPath $out -Recurse -Force
}
New-Item -ItemType Directory -Path $classes -Force | Out-Null

$sources = @(Get-ChildItem -LiteralPath (Join-Path $root "src\main\java") -Recurse -Filter *.java -File |
    ForEach-Object { $_.FullName })
if ($sources.Count -eq 0) {
    Write-Host "OPS PROOF BUILD ok=false reason=no-sources"
    exit 1
}

Write-Host "OPS PROOF BUILD java=$jdk"
& $javac --release 26 -encoding UTF-8 -cp $compileCp -d $classes @sources
if ($LASTEXITCODE -ne 0) {
    Write-Host "OPS PROOF BUILD ok=false reason=javac exit=$LASTEXITCODE"
    exit 1
}
Write-Host "OPS PROOF BUILD ok=true classes=$classes"

$runCp = "$compileCp;$classes"
& $java "-Dweaver.ops.agentJar=$agentJar" -cp $runCp "net.weaver.opsproof.OpsProof"
$proofExit = $LASTEXITCODE
if ($proofExit -ne 0) {
    Write-Host "OPS PROOF RUN ok=false exit=$proofExit"
    exit 1
}
Write-Host "OPS PROOF RUN ok=true"

if ($Shared) {
    $sharedScript = Join-Path $repo "examples\proof\run-server-shared.ps1"
    if (-not (Test-Path $sharedScript)) {
        Write-Host "OPS SHARED PROOF ok=false reason=missing-script path=$sharedScript"
        exit 1
    }

    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $sharedOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $sharedScript 2>&1 |
        ForEach-Object { "$_" }
    $sharedExit = $LASTEXITCODE
    $ErrorActionPreference = $previous

    Write-Host "OPS SHARED PROOF output:"
    $sharedOutput | ForEach-Object { Write-Host $_ }
    if ($sharedExit -ne 0) {
        Write-Host "OPS SHARED PROOF ok=false exit=$sharedExit"
        exit 1
    }
    if (($sharedOutput -join "`n") -notmatch "SHARED MODE RESULT ok=true") {
        Write-Host "OPS SHARED PROOF ok=false reason=missing-marker"
        exit 1
    }
    Write-Host "OPS SHARED PROOF ok=true"
}

exit 0
