$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $root))
$out = Join-Path $root "build"
$classes = Join-Path $out "classes"
$fixtureJar = Join-Path $out "fixture.jar"
$buildScript = Join-Path $root "build.ps1"

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates = @(& (Join-Path $PSScriptRoot '..\..\..\tools\jdk.ps1')) + $candidates + @("C:\Program Files\Java\jdk-26.0.1")
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "bin\java.exe")) { return $candidate }
    }
    throw "No JDK 26 found. Set JAVA_HOME."
}

function Get-LatestJar {
    param([string]$Directory, [string]$Pattern)
    $jar = Get-ChildItem -LiteralPath $Directory -Filter $Pattern -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $jar) {
        throw "Missing jar $Pattern in $Directory (run .\gradlew.bat :api:jar :core:jar)"
    }
    return $jar
}

$jdk = Find-Jdk
$java = Join-Path $jdk "bin\java.exe"
$apiJar = Get-LatestJar (Join-Path $repo "api\build\libs") "api-*.jar"
$coreJar = Get-LatestJar (Join-Path $repo "core\build\libs") "core-*.jar"

$benchSource = Join-Path $root "src\main\java\net\weaver\bench\PlatformBenchmark.java"
$needsBuild = -not (Test-Path $classes) -or -not (Test-Path $fixtureJar)
if (-not $needsBuild) {
    $classStamp = (Get-Item $classes).LastWriteTimeUtc
    $needsBuild = (Get-Item $benchSource).LastWriteTimeUtc -gt $classStamp -or
        $apiJar.LastWriteTimeUtc -gt $classStamp -or
        $coreJar.LastWriteTimeUtc -gt $classStamp
}
if ($needsBuild) {
    & $buildScript
}
$agentLib = Join-Path $repo "core\build\agent\lib"
if (-not (Test-Path $agentLib)) {
    throw "Missing agent lib: $agentLib (run .\gradlew.bat :core:agentJar)"
}
$libCp = (Get-ChildItem -LiteralPath $agentLib -Filter *.jar -File |
    ForEach-Object { $_.FullName }) -join ";"
$benchCp = "$classes;$($apiJar.FullName);$($coreJar.FullName);$libCp"

& $java "-cp" $benchCp "net.weaver.bench.PlatformBenchmark" $fixtureJar
$exit = $LASTEXITCODE
if ($exit -ne 0) {
    throw "PLATFORM_BENCH BUDGET ok=false exit=$exit"
}
