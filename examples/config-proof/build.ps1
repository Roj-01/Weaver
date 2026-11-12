$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$agentDir = Join-Path $repo "core\build\agent"
$agentLib = Join-Path $agentDir "lib"

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + $candidates + @("C:\Program Files\Java\jdk-26.0.1")
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "bin\javac.exe")) { return $candidate }
    }
    throw "No JDK 26 found. Set JAVA_HOME to a JDK 26 installation."
}

$jdk = Find-Jdk
$javac = Join-Path $jdk "bin\javac.exe"

if (-not (Test-Path -LiteralPath $agentLib)) {
    throw "agent lib missing: $agentLib`nRemediation: run .\gradlew.bat :core:agentJar"
}

$libJars = @(Get-ChildItem -LiteralPath $agentLib -Filter *.jar -File)
if ($libJars.Count -eq 0) {
    throw "agent lib has no jars: $agentLib`nRemediation: run .\gradlew.bat :core:agentJar"
}

$coreCandidates = @()
$coreLibs = Join-Path $repo "core\build\libs"
if (Test-Path -LiteralPath $coreLibs) {
    $coreCandidates += @(Get-ChildItem -LiteralPath $coreLibs -Filter "core-*.jar" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" })
}
$agentJar = Join-Path $agentDir "weaver-agent.jar"
if (Test-Path -LiteralPath $agentJar) {
    $coreCandidates += @(Get-Item -LiteralPath $agentJar)
}
$coreJar = $coreCandidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $coreJar) {
    throw "core classes jar missing under core\build\libs`nRemediation: run .\gradlew.bat :core:agentJar or .\gradlew.bat :core:jar"
}

$compileCp = ((@($libJars | ForEach-Object { $_.FullName }) + @($coreJar.FullName)) -join ";")

if (Test-Path -LiteralPath $out) {
    Remove-Item -LiteralPath $out -Recurse -Force
}
$classes = Join-Path $out "classes"
New-Item -ItemType Directory -Path $classes -Force | Out-Null

$sources = Get-ChildItem -LiteralPath (Join-Path $root "src\main\java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
if ($sources.Count -eq 0) { throw "No Java sources under src\main\java" }

& $javac --release 26 -encoding UTF-8 -cp $compileCp -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

Set-Content -LiteralPath (Join-Path $out "run-classpath.txt") -Value "$classes;$agentLib\*;$($coreJar.FullName)" -Encoding ASCII

Write-Host "CONFIG PROOF BUILD ok=true classes=$classes"
