$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path $root "out"

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += "C:\Program Files\Java\jdk-26.0.1"
    foreach ($candidate in $candidates) {
        $javac = Join-Path $candidate "bin\javac.exe"
        if (Test-Path $javac) { return $candidate }
    }
    throw "No JDK 26 found. Set JAVA_HOME."
}

$jdk = Find-Jdk
$javac = Join-Path $jdk "bin\javac.exe"
$jarTool = Join-Path $jdk "bin\jar.exe"
$java = Join-Path $jdk "bin\java.exe"

$mixinAgent = $env:WEAVER_MIXIN_AGENT
if (-not $mixinAgent) { throw "WEAVER_MIXIN_AGENT is required for this historical spike" }
$mixinLibs = $env:WEAVER_MIXIN_LIBS
if (-not $mixinLibs) { throw "WEAVER_MIXIN_LIBS is required for this historical spike" }

if (-not (Test-Path $mixinAgent)) { throw "Mixin agent jar not found: $mixinAgent" }
if (-not (Test-Path $mixinLibs)) { throw "Mixin libraries not found: $mixinLibs" }

$libJars = Get-ChildItem -LiteralPath $mixinLibs -Filter *.jar | ForEach-Object { $_.FullName }
if ($libJars.Count -eq 0) { throw "No jars under $mixinLibs" }
$mixinClasspath = $libJars -join ";"

if (Test-Path $out) { Remove-Item -LiteralPath $out -Recurse -Force }
New-Item -ItemType Directory -Path $out | Out-Null
foreach ($name in @("game-classes", "mod-classes", "agent-classes", "main-classes")) {
    New-Item -ItemType Directory -Path (Join-Path $out $name) | Out-Null
}

function Compile([string]$sourceRoot, [string]$output, [string]$classpath) {
    $sources = Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    if ($sources.Count -eq 0) { throw "No Java sources under $sourceRoot" }
    $arguments = @("--release", "25", "-encoding", "UTF-8")
    if ($classpath) {
        $arguments += @("-cp", $classpath)
    }
    $arguments += @("-d", $output)
    $arguments += $sources
    & $javac $arguments
    if ($LASTEXITCODE -ne 0) { throw "javac failed for $sourceRoot" }
}

Compile (Join-Path $root "src\game") (Join-Path $out "game-classes") ""
& $jarTool --create --file (Join-Path $out "game.jar") -C (Join-Path $out "game-classes") .
if ($LASTEXITCODE -ne 0) { throw "jar failed for game.jar" }

Compile (Join-Path $root "src\mod") (Join-Path $out "mod-classes") ((Join-Path $out "game.jar") + ";" + $mixinClasspath)
Copy-Item -LiteralPath (Join-Path $root "src\mod\spike.mixins.json") -Destination (Join-Path $out "mod-classes\spike.mixins.json")
& $jarTool --create --file (Join-Path $out "mod.jar") -C (Join-Path $out "mod-classes") .
if ($LASTEXITCODE -ne 0) { throw "jar failed for mod.jar" }

Compile (Join-Path $root "src\agent") (Join-Path $out "agent-classes") ""
$manifest = Join-Path $out "agent.mf"
Set-Content -LiteralPath $manifest -Value "Premain-Class: net.example.agent.ProbeAgent`r`n" -NoNewline
& $jarTool --create --file (Join-Path $out "probe-agent.jar") --manifest $manifest -C (Join-Path $out "agent-classes") .
if ($LASTEXITCODE -ne 0) { throw "jar failed for probe-agent.jar" }

Compile (Join-Path $root "src\main") (Join-Path $out "main-classes") ""
& $jarTool --create --file (Join-Path $out "main.jar") -C (Join-Path $out "main-classes") .
if ($LASTEXITCODE -ne 0) { throw "jar failed for main.jar" }

$gameJar = Join-Path $out "game.jar"
$modJar = Join-Path $out "mod.jar"
$mainJar = Join-Path $out "main.jar"
$probeAgent = Join-Path $out "probe-agent.jar"

Write-Host "CLASSLOADING SPIKE system-mode start"
& $java "-javaagent:$mixinAgent" "-javaagent:$probeAgent=mode=system;modJar=$modJar;config=spike.mixins.json" -cp "$gameJar;$mainJar" net.example.probe.Main "--mode=system" "--game=$gameJar" "--mod=$modJar"
if ($LASTEXITCODE -ne 0) { throw "system-mode spike failed" }

Write-Host "CLASSLOADING SPIKE shared-mode start"
& $java "-javaagent:$mixinAgent" "-javaagent:$probeAgent=mode=shared" -cp "$mainJar" net.example.probe.Main "--mode=shared" "--game=$gameJar" "--mod=$modJar"
if ($LASTEXITCODE -ne 0) { throw "shared-mode spike failed" }

Write-Host "CLASSLOADING SPIKE RESULT system=ok shared=ok"
