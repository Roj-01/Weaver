$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$classes = Join-Path $out "classes"
$jarPath = Join-Path $out "network-proof.jar"
$classPathFile = Join-Path $out "classpath.txt"

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + $candidates + @("C:\Program Files\Java\jdk-26.0.1")
    foreach ($candidate in $candidates) {
        $javac = Join-Path $candidate "bin\javac.exe"
        if (Test-Path -LiteralPath $javac) { return $candidate }
    }
    throw "No JDK 26 found. Set JAVA_HOME or install C:\Program Files\Java\jdk-26.0.1."
}

function Find-Jar([string]$directory, [string]$pattern) {
    if (-not (Test-Path -LiteralPath $directory)) { return $null }
    $found = @(Get-ChildItem -LiteralPath $directory -Filter $pattern -File |
        Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } |
        Sort-Object LastWriteTime -Descending)
    if ($found.Count -eq 0) { return $null }
    return $found[0].FullName
}

$jdk = Find-Jdk
$javac = Join-Path $jdk "bin\javac.exe"
$jarTool = Join-Path $jdk "bin\jar.exe"

$apiLib = Join-Path $repo "api\build\libs"
$coreLib = Join-Path $repo "core\build\libs"
$apiGameLib = Join-Path $repo "api-game\build\libs"
$apiJar = Find-Jar $apiLib "api-*.jar"
$coreJar = Find-Jar $coreLib "core-*.jar"
$apiGameJar = Find-Jar $apiGameLib "api-game-*.jar"

if (-not $apiJar -or -not $coreJar) {
    $gradlew = Join-Path $repo "gradlew.bat"
    if (-not (Test-Path -LiteralPath $gradlew)) {
        throw "gradlew.bat missing: $gradlew"
    }
    Write-Host "NETWORK PROOF BUILD building-api-core"
    Push-Location -LiteralPath $repo
    try {
        & $gradlew ":api:jar" ":core:jar" "--no-daemon"
    }
    finally {
        Pop-Location
    }
    if ($LASTEXITCODE -ne 0) {
        throw "gradle :api:jar :core:jar failed with exit code $LASTEXITCODE"
    }
    $apiJar = Find-Jar $apiLib "api-*.jar"
    $coreJar = Find-Jar $coreLib "core-*.jar"
}

if (-not $apiJar) { throw "api jar missing: $apiLib" }
if (-not $coreJar) { throw "core jar missing: $coreLib" }

$bridgeClassInJar = $false
if ($apiGameJar) {
    $entries = @(& $jarTool --list --file $apiGameJar)
    $bridgeClassInJar = @($entries | Where-Object {
            $_ -eq "net/weaver/api/game/network/BridgeSession.class" }).Count -gt 0
}
if (-not $apiGameJar -or -not $bridgeClassInJar) {
    $gradlew = Join-Path $repo "gradlew.bat"
    if (Test-Path -LiteralPath $gradlew) {
        Write-Host "NETWORK BRIDGE PROOF BUILD building-api-game"
        Push-Location -LiteralPath $repo
        try {
            & $gradlew ":api-game:jar" "--no-daemon"
        }
        finally {
            Pop-Location
        }
        if ($LASTEXITCODE -eq 0) {
            $apiGameJar = Find-Jar $apiGameLib "api-game-*.jar"
        }
        else {
            $apiGameJar = $null
        }
    }
}

if (Test-Path -LiteralPath $out) {
    Remove-Item -LiteralPath $out -Recurse -Force
}
New-Item -ItemType Directory -Path $classes -Force | Out-Null

$sources = @(Get-ChildItem -LiteralPath (Join-Path $root "src\main\java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName })
if ($sources.Count -eq 0) {
    throw "No proof Java sources found under $root\src\main\java"
}

$compileClassPath = "$apiJar;$coreJar"
& $javac --release 26 -encoding UTF-8 -cp $compileClassPath -d $classes $sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

$bridgeCompiled = $false
if ($apiGameJar) {
    $bridgeSourceRoot = Join-Path $root "src\bridge\java"
    if (Test-Path -LiteralPath $bridgeSourceRoot) {
        $bridgeSources = @(Get-ChildItem -LiteralPath $bridgeSourceRoot -Recurse -Filter *.java |
            ForEach-Object { $_.FullName })
        if ($bridgeSources.Count -gt 0) {
            $bridgeClassPath = "$apiJar;$coreJar;$apiGameJar"
            & $javac --release 26 -encoding UTF-8 -cp $bridgeClassPath -d $classes $bridgeSources
            if ($LASTEXITCODE -ne 0) {
                throw "bridge contract javac failed with exit code $LASTEXITCODE"
            }
            $bridgeCompiled = $true
        }
    }
}

& $jarTool --create --file $jarPath -C $classes .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE"
}

$runtimeClassPath = "$jarPath;$apiJar;$coreJar"
if ($bridgeCompiled) {
    $runtimeClassPath = "$runtimeClassPath;$apiGameJar"
}
Set-Content -LiteralPath $classPathFile -Value $runtimeClassPath -Encoding ASCII

if ($bridgeCompiled) {
    Write-Host "NETWORK BRIDGE PROOF BUILD ok=true jar=$jarPath"
}
else {
    Write-Host "NETWORK BRIDGE PROOF BUILD SKIP ok=true reason=api-game-unavailable"
}
Write-Host "NETWORK PROOF BUILD ok=true jar=$jarPath"
