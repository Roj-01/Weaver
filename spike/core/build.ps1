$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$agent = Join-Path $repo "core\build\agent\weaver-agent.jar"
$agentLib = Join-Path $repo "core\build\agent\lib"

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += "C:\Program Files\Java\jdk-26.0.1"
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "bin\javac.exe")) { return $candidate }
    }
    throw "No JDK 26 found. Set JAVA_HOME."
}

$jdk = Find-Jdk
$javac = Join-Path $jdk "bin\javac.exe"
$jarTool = Join-Path $jdk "bin\jar.exe"
$java = Join-Path $jdk "bin\java.exe"

if (-not (Test-Path $agent)) { throw "agent jar missing: $agent (run .\gradlew.bat :core:agentJar)" }
if (-not (Test-Path $agentLib)) { throw "agent lib missing: $agentLib" }

$libJars = Get-ChildItem -LiteralPath $agentLib -Filter *.jar | ForEach-Object { $_.FullName }
if ($libJars.Count -eq 0) { throw "No jars under $agentLib" }
$libClasspath = $libJars -join ";"

if (Test-Path $out) { Remove-Item -LiteralPath $out -Recurse -Force }
foreach ($name in @("game-real-classes", "game-stub-classes", "mod-classes", "main-classes", "mods", "config", "data")) {
    New-Item -ItemType Directory -Path (Join-Path $out $name) | Out-Null
}

function Compile([string[]]$sourceRoots, [string]$output, [string]$classpath) {
    $sources = @()
    foreach ($sourceRoot in $sourceRoots) {
        $sources += Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    }
    if ($sources.Count -eq 0) { throw "No Java sources under $sourceRoots" }
    $arguments = @("--release", "25", "-encoding", "UTF-8")
    if ($classpath) { $arguments += @("-cp", $classpath) }
    $arguments += @("-d", $output)
    $arguments += $sources
    & $javac $arguments
    if ($LASTEXITCODE -ne 0) { throw "javac failed for $sourceRoots" }
}

$common = Join-Path $root "src\game-common"
$real = Join-Path $root "src\game-real"
$stub = Join-Path $root "src\game-stub"

Compile @($common, $real) (Join-Path $out "game-real-classes") ""
& $jarTool --create --file (Join-Path $out "game-real.jar") -C (Join-Path $out "game-real-classes") .
if ($LASTEXITCODE -ne 0) { throw "jar failed for game-real.jar" }

Compile @($common, $stub) (Join-Path $out "game-stub-classes") ""
& $jarTool --create --file (Join-Path $out "game-stub.jar") -C (Join-Path $out "game-stub-classes") .
if ($LASTEXITCODE -ne 0) { throw "jar failed for game-stub.jar" }

$modSource = Join-Path $root "src\mod"
Compile @($modSource) (Join-Path $out "mod-classes") ((Join-Path $out "game-stub.jar") + ";" + $libClasspath)
Copy-Item -LiteralPath (Join-Path $modSource "weaver.mod.json") -Destination (Join-Path $out "mod-classes\weaver.mod.json")
Copy-Item -LiteralPath (Join-Path $modSource "proof.mixins.json") -Destination (Join-Path $out "mod-classes\proof.mixins.json")
New-Item -ItemType Directory -Path (Join-Path $out "mod-classes\META-INF") -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $modSource "META-INF\accesstransformer.cfg") -Destination (Join-Path $out "mod-classes\META-INF\accesstransformer.cfg")
& $jarTool --create --file (Join-Path $out "mods\proof.jar") -C (Join-Path $out "mod-classes") .
if ($LASTEXITCODE -ne 0) { throw "jar failed for proof.jar" }

Compile @(Join-Path $root "src\main") (Join-Path $out "main-classes") (Join-Path $out "game-real.jar")
& $jarTool --create --file (Join-Path $out "main.jar") -C (Join-Path $out "main-classes") .
if ($LASTEXITCODE -ne 0) { throw "jar failed for main.jar" }

$gameJar = Join-Path $out "game-real.jar"
$mainJar = Join-Path $out "main.jar"
$modsDir = Join-Path $out "mods"
$configDir = Join-Path $out "config"
$dataDir = Join-Path $out "data"

function Run-Spike([string]$side) {
    Write-Host "CORE SPIKE side=$side start"
    & $java "-javaagent:$agent=modsDir=$modsDir;side=$side;configDir=$configDir;dataDir=$dataDir" -cp "$gameJar;$mainJar" net.example.probe.SpikeMain $side
    if ($LASTEXITCODE -ne 0) { throw "core spike failed side=$side" }
}

Run-Spike "client"
Run-Spike "server"

Write-Host "CORE SPIKE RESULT client=ok server=ok"
