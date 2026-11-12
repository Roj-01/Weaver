# Prints the JDK home directories that can build and run Weaver, best first.
#
# Every build and proof script calls this before its own legacy lookup, so a
# fresh machine works without editing scripts or setting JAVA_HOME:
#
#   $candidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + $candidates
#
# Order: WEAVER_JDK, JAVA_HOME, then installed JDKs found in the usual places
# (Program Files\Java, Eclipse Adoptium, Microsoft, Zulu, %USERPROFILE%\.jdks
# as used by IntelliJ and Gradle discovery, and Gradle's auto-provisioned
# %USERPROFILE%\.gradle\jdks). Only directories with bin\javac.exe whose
# `release` file reports the required major version are printed, so a
# JAVA_HOME that points at an older JDK is skipped instead of breaking
# `javac --release 26`.
param(
    [int]$MajorVersion = 26
)

$ErrorActionPreference = 'Stop'

function Get-JdkMajorVersion([string]$jdkHome) {
    $release = Join-Path $jdkHome 'release'
    if (-not (Test-Path -LiteralPath $release -PathType Leaf)) { return $null }
    $line = Get-Content -LiteralPath $release -ErrorAction SilentlyContinue |
        Where-Object { $_ -match '^JAVA_VERSION=' } | Select-Object -First 1
    if ($line -match '^JAVA_VERSION="?(\d+)') { return [int]$Matches[1] }
    return $null
}

$roots = @()
if ($env:WEAVER_JDK) { $roots += $env:WEAVER_JDK }
if ($env:JAVA_HOME) { $roots += $env:JAVA_HOME }

$searchDirs = @(
    (Join-Path $env:ProgramFiles 'Java'),
    (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
    (Join-Path $env:ProgramFiles 'Microsoft'),
    (Join-Path $env:ProgramFiles 'Zulu'),
    (Join-Path $env:USERPROFILE '.jdks'),
    (Join-Path $env:USERPROFILE '.gradle\jdks')
)
foreach ($dir in $searchDirs) {
    if (-not $dir -or -not (Test-Path -LiteralPath $dir -PathType Container)) { continue }
    $roots += @(Get-ChildItem -LiteralPath $dir -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | ForEach-Object { $_.FullName })
}

$seen = @{}
foreach ($root in $roots) {
    if (-not $root) { continue }
    $full = [System.IO.Path]::GetFullPath($root.Trim())
    if ($seen.ContainsKey($full)) { continue }
    $seen[$full] = $true
    if (-not (Test-Path -LiteralPath (Join-Path $full 'bin\javac.exe') -PathType Leaf)) { continue }
    if ((Get-JdkMajorVersion $full) -eq $MajorVersion) { $full }
}
