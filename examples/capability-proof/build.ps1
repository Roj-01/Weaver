$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$gameDir = if ($env:WEAVER_GAME_DIR) { $env:WEAVER_GAME_DIR } else { Join-Path $repo "work\game-26.3" }
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

function Test-JarEntry($jarPath, $entryName) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        return $null -ne $archive.GetEntry($entryName)
    }
    finally {
        $archive.Dispose()
    }
}

$jdk = Find-Jdk
$javac = Join-Path $jdk "bin\javac.exe"

$classpathFile = Join-Path $gameDir "classpath.txt"
if (-not (Test-Path -LiteralPath $classpathFile)) {
    throw "game classpath missing: $classpathFile`nSet WEAVER_GAME_DIR to a prepared game directory that contains classpath.txt."
}
$gameCp = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
if ([string]::IsNullOrWhiteSpace($gameCp)) {
    throw "game classpath is empty: $classpathFile"
}

$apiCandidates = @()
foreach ($directory in @((Join-Path $repo "api\build\libs"), $agentLib)) {
    if (Test-Path -LiteralPath $directory) {
        $apiCandidates += @(Get-ChildItem -LiteralPath $directory -Filter "api-*.jar" -File -ErrorAction SilentlyContinue |
            Where-Object {
                $_.Name -notlike "*-sources.jar" -and
                $_.Name -notlike "*-javadoc.jar" -and
                $_.Name -notlike "api-game-*"
            })
    }
}
$apiJar = $apiCandidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apiJar) {
    throw "api jar missing under api\build\libs or core\build\agent\lib`nRemediation: run .\gradlew.bat :api:jar"
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
    throw "core jar missing under core\build\libs and core\build\agent\weaver-agent.jar is absent`nRemediation: run .\gradlew.bat :core:jar or .\gradlew.bat :core:agentJar"
}

if (-not (Test-Path -LiteralPath $agentLib)) {
    throw "agent lib missing: $agentLib`nRemediation: run .\gradlew.bat :core:agentJar"
}
$libJars = @(Get-ChildItem -LiteralPath $agentLib -Filter *.jar -File)
if ($libJars.Count -eq 0) {
    throw "agent lib has no jars: $agentLib`nRemediation: run .\gradlew.bat :core:agentJar"
}

if (Test-Path -LiteralPath $out) {
    Remove-Item -LiteralPath $out -Recurse -Force
}
$classes = Join-Path $out "classes"
New-Item -ItemType Directory -Path $classes -Force | Out-Null

$coreClasses = $null
if ((-not (Test-JarEntry $coreJar.FullName "net/weaver/core/capability/CapabilityStores.class")) -or
    (-not (Test-JarEntry $coreJar.FullName "net/weaver/core/capability/CapabilityPersistence.class"))) {
    Write-Host "CAPABILITY PROOF BUILD note=core-jar-stale action=compile-core-sources"
    $coreClasses = Join-Path $out "core-classes"
    New-Item -ItemType Directory -Path $coreClasses -Force | Out-Null
    $coreSources = @()
    foreach ($package in @("data", "capability")) {
        $sourceDir = Join-Path $repo "core\src\main\java\net\weaver\core\$package"
        if (-not (Test-Path -LiteralPath $sourceDir)) {
            throw "core source package missing: $sourceDir"
        }
        $coreSources += @(Get-ChildItem -LiteralPath $sourceDir -Filter *.java -File |
            ForEach-Object { $_.FullName })
    }
    if ($coreSources.Count -eq 0) { throw "No core data or capability sources found" }
    & $javac --release 26 -encoding UTF-8 -cp $apiJar.FullName -d $coreClasses $coreSources
    if ($LASTEXITCODE -ne 0) {
        throw "javac failed compiling core fallback classes with exit code $LASTEXITCODE"
    }
}

$compileEntries = @($apiJar.FullName)
if ($coreClasses) { $compileEntries += $coreClasses }
$compileEntries += $coreJar.FullName
$compileEntries += @($libJars | ForEach-Object { $_.FullName })
$compileEntries += $gameCp
$compileCp = ($compileEntries -join ";")

$sources = @(Get-ChildItem -LiteralPath (Join-Path $root "src\main\java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName })
if ($sources.Count -eq 0) { throw "No Java sources under src\main\java" }

& $javac --release 26 -encoding UTF-8 -cp $compileCp -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

$runtimeEntries = @($classes, $apiJar.FullName)
if ($coreClasses) { $runtimeEntries += $coreClasses }
$runtimeEntries += $coreJar.FullName
$runtimeEntries += @($libJars | ForEach-Object { $_.FullName })
$runtimeEntries += $gameCp
$runtimeCp = ($runtimeEntries -join ";")

Set-Content -LiteralPath (Join-Path $out "run-classpath.txt") -Value $runtimeCp -Encoding ASCII

Write-Host "CAPABILITY PROOF BUILD ok=true classes=$classes"
