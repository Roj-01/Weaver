$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $root))
$out = Join-Path $root "build"
$classes = Join-Path $out "classes"
$fixtureSrc = Join-Path $out "fixture-src"
$fixtureClasses = Join-Path $out "fixture-classes"
$fixtureJar = Join-Path $out "fixture.jar"
$subscribers = 100
$plainClasses = 100

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates = @(& (Join-Path $PSScriptRoot '..\..\..\tools\jdk.ps1')) + $candidates + @("C:\Program Files\Java\jdk-26.0.1")
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "bin\javac.exe")) { return $candidate }
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
$javac = Join-Path $jdk "bin\javac.exe"
$jarTool = Join-Path $jdk "bin\jar.exe"
$apiJar = Get-LatestJar (Join-Path $repo "api\build\libs") "api-*.jar"
$coreJar = Get-LatestJar (Join-Path $repo "core\build\libs") "core-*.jar"
$agentLib = Join-Path $repo "core\build\agent\lib"
if (-not (Test-Path $agentLib)) {
    throw "Missing agent lib: $agentLib (run .\gradlew.bat :core:agentJar)"
}
$libJars = Get-ChildItem -LiteralPath $agentLib -Filter *.jar -File
$libCp = ($libJars | ForEach-Object { $_.FullName }) -join ";"
$compileCp = "$($apiJar.FullName);$($coreJar.FullName);$libCp"

if (Test-Path $out) { Remove-Item -LiteralPath $out -Recurse -Force }
New-Item -ItemType Directory -Path $classes | Out-Null
New-Item -ItemType Directory -Path $fixtureSrc | Out-Null
New-Item -ItemType Directory -Path $fixtureClasses | Out-Null

$source = Join-Path $root "src\main\java\net\weaver\bench\PlatformBenchmark.java"
& $javac --release 26 -encoding UTF-8 -cp $compileCp -d $classes $source
if ($LASTEXITCODE -ne 0) { throw "javac failed for PlatformBenchmark" }

$fixturePackage = Join-Path $fixtureSrc "bench\fixture"
New-Item -ItemType Directory -Path $fixturePackage | Out-Null

$subscriberTemplate = @'
package bench.fixture;

import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.SubscribeEvent;
import net.weaver.api.event.events.ServerTickEvent;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, modId = "bench-fixture")
public final class __NAME__ {
    private __NAME__() {
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent event) {
    }
}
'@

$plainTemplate = @'
package bench.fixture;

public final class __NAME__ {
    private int value;

    public int value() {
        return value;
    }
}
'@

for ($index = 0; $index -lt $subscribers; $index++) {
    $name = "Subscriber$index"
    $text = $subscriberTemplate.Replace("__NAME__", $name)
    [System.IO.File]::WriteAllText((Join-Path $fixturePackage "$name.java"), $text)
}
for ($index = 0; $index -lt $plainClasses; $index++) {
    $name = "Plain$index"
    $text = $plainTemplate.Replace("__NAME__", $name)
    [System.IO.File]::WriteAllText((Join-Path $fixturePackage "$name.java"), $text)
}

$mainText = @'
package bench.fixture;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
    }
}
'@
[System.IO.File]::WriteAllText((Join-Path $fixturePackage "Main.java"), $mainText)

$metadata = '{"schemaVersion":2,"id":"bench-fixture","version":"1.0.0","name":"bench-fixture","mainClass":"bench.fixture.Main"}'
[System.IO.File]::WriteAllText((Join-Path $fixtureClasses "weaver.mod.json"), $metadata)

$fixtureSources = Get-ChildItem -LiteralPath $fixtureSrc -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
& $javac --release 26 -encoding UTF-8 -cp $apiJar.FullName -d $fixtureClasses $fixtureSources
if ($LASTEXITCODE -ne 0) { throw "javac failed for the scanner fixture" }

& $jarTool --create --file $fixtureJar -C $fixtureClasses .
if ($LASTEXITCODE -ne 0) { throw "jar failed for the scanner fixture" }

Write-Host "BENCH BUILD ok=true classes=$classes fixture=$fixtureJar subscribers=$subscribers plain=$plainClasses"
