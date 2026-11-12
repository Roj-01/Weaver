#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$ClientJar = "",
    [string]$ServerJar = "",
    [string]$OutDir = "",
    [string]$InjectDir = "",
    [int]$PatchLevel = 1
)

$ErrorActionPreference = "Stop"

$root = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$repo = Split-Path -Parent (Split-Path -Parent $root)
$buildScript = Join-Path $root "build.ps1"
$classpathFile = Join-Path $root "out\patcher-classpath.txt"

function Get-FullPath {
    param([Parameter(Mandatory = $true)][string]$Path)
    return $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Path)
}

if (-not $ClientJar) { $ClientJar = Join-Path $repo "work\game-26.3\client.jar" }
if (-not $ServerJar) { $ServerJar = Join-Path $repo "work\server-26.3\versions\26.3\server-26.3.jar" }
if (-not $OutDir) { $OutDir = Join-Path $root "out" }
if (-not $InjectDir) { $InjectDir = Join-Path $root "out\inject" }

$ClientJar = Get-FullPath $ClientJar
$ServerJar = Get-FullPath $ServerJar
$OutDir = Get-FullPath $OutDir
$InjectDir = Get-FullPath $InjectDir

if (-not (Test-Path -LiteralPath $ClientJar -PathType Leaf)) {
    throw "client jar not found: $ClientJar"
}
if (-not (Test-Path -LiteralPath $ServerJar -PathType Leaf)) {
    throw "server jar not found: $ServerJar"
}

$jdkCandidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + @($env:JAVA_HOME, "C:\Program Files\Java\jdk-26.0.1") |
    Where-Object { $_ -and $_.Trim().Length -gt 0 }
$jdk = $null
foreach ($candidate in $jdkCandidates) {
    if (Test-Path -LiteralPath (Join-Path $candidate "bin\java.exe") -PathType Leaf) {
        $jdk = $candidate
        break
    }
}
if (-not $jdk) {
    throw "No JDK found. Set JAVA_HOME or install C:\Program Files\Java\jdk-26.0.1."
}
$java = Join-Path $jdk "bin\java.exe"

$requiredOutputs = @(
    $classpathFile,
    (Join-Path $root "out\classes\net\weaver\patcher\Main.class"),
    (Join-Path $root "out\classes\net\weaver\patcher\JarPatcher.class"),
    (Join-Path $InjectDir "net\weaver\patched\PatchedGame.class"),
    (Join-Path $InjectDir "net\weaver\patched\WeaverAttachmentHolder.class"),
    (Join-Path $InjectDir "net\weaver\patched\WeaverAttachmentStore.class")
)

$needsBuild = $false
foreach ($required in $requiredOutputs) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        $needsBuild = $true
        break
    }
}
if (-not $needsBuild) {
    $oldestOutput = ($requiredOutputs | ForEach-Object { (Get-Item -LiteralPath $_).LastWriteTimeUtc } |
        Measure-Object -Minimum).Minimum
    $sourceDirectories = @((Join-Path $root "src"), (Join-Path $root "inject"), (Join-Path $root "test"))
    $newestSource = (Get-ChildItem -LiteralPath $sourceDirectories -Recurse -File |
        Measure-Object -Maximum LastWriteTimeUtc).Maximum
    if ($newestSource -gt $oldestOutput) {
        $needsBuild = $true
    }
}

if ($needsBuild) {
    & $buildScript
    if (-not $?) {
        throw "patcher build failed"
    }
}

if (-not (Test-Path -LiteralPath $classpathFile -PathType Leaf)) {
    throw "patcher classpath file missing: $classpathFile"
}
$toolClasspath = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
if (-not $toolClasspath) {
    throw "patcher classpath file is empty: $classpathFile"
}

New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
$clientOutput = Join-Path $OutDir "client-patched.jar"
$serverOutput = Join-Path $OutDir "server-patched.jar"
$clientReport = Join-Path $OutDir "report-client.json"
$serverReport = Join-Path $OutDir "report-server.json"

function Invoke-Patcher {
    param(
        [Parameter(Mandatory = $true)][string]$InputJar,
        [Parameter(Mandatory = $true)][string]$OutputJar,
        [Parameter(Mandatory = $true)][string]$Report
    )
    & $java -cp $toolClasspath net.weaver.patcher.Main `
        --input $InputJar `
        --output $OutputJar `
        --inject $InjectDir `
        --report $Report `
        --patch-level $PatchLevel
    if ($LASTEXITCODE -ne 0) {
        throw "patcher failed for $InputJar (exit code $LASTEXITCODE)"
    }
}

try {
    Invoke-Patcher -InputJar $ClientJar -OutputJar $clientOutput -Report $clientReport
    Invoke-Patcher -InputJar $ServerJar -OutputJar $serverOutput -Report $serverReport
} catch {
    Remove-Item -LiteralPath $clientOutput, $serverOutput, $clientReport, $serverReport `
        -Force -ErrorAction SilentlyContinue
    throw
}

Write-Host "PATCHER GAME ok=true client=$clientOutput server=$serverOutput"
