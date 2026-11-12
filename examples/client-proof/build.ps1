$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$gameDir = if ($env:WEAVER_GAME_DIR) { $env:WEAVER_GAME_DIR } else { Join-Path $repo "work\game-26.3" }
$agentLib = Join-Path $repo "core\build\agent\lib"
$agentJar = Join-Path $repo "core\build\agent\weaver-agent.jar"

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + $candidates + @("C:\Program Files\Java\jdk-26.0.1")
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "bin\javac.exe")) { return $candidate }
    }
    throw "No JDK 26 found. Set JAVA_HOME."
}

$jdk = Find-Jdk
$javac = Join-Path $jdk "bin\javac.exe"
$jarTool = Join-Path $jdk "bin\jar.exe"

$classpathFile = Join-Path $gameDir "classpath.txt"
if (-not (Test-Path $classpathFile)) { throw "game classpath missing: $classpathFile" }
if (-not (Test-Path $agentLib)) { throw "agent lib missing: $agentLib (run .\gradlew.bat :core:agentJar)" }
if (-not (Test-Path $agentJar)) { throw "agent jar missing: $agentJar (run .\gradlew.bat :core:agentJar)" }

$gameCp = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
$libCp = (Get-ChildItem -LiteralPath $agentLib -Filter *.jar | ForEach-Object { $_.FullName }) -join ";"
$apiGameJar = Get-ChildItem -LiteralPath (Join-Path $repo "api-game\build\libs") -Filter "api-game-*.jar" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $apiGameJar) {
    $apiGameJar = Get-ChildItem -LiteralPath $agentLib -Filter "api-game-*.jar" -File -ErrorAction SilentlyContinue |
        Select-Object -First 1
}
if (-not $apiGameJar) { throw "api-game jar missing: run .\gradlew.bat :api-game:jar" }
$compileCp = "$gameCp;$libCp;$agentJar;$($apiGameJar.FullName)"

if (Test-Path $out) { Remove-Item -LiteralPath $out -Recurse -Force }
New-Item -ItemType Directory -Path (Join-Path $out "classes") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $out "mods") | Out-Null

$sources = Get-ChildItem -LiteralPath (Join-Path $root "src\main\java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
if ($sources.Count -eq 0) { throw "No Java sources" }

& $javac --release 26 -encoding UTF-8 -cp $compileCp -d (Join-Path $out "classes") $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Copy-Item -Path (Join-Path $root "src\main\resources\*") -Destination (Join-Path $out "classes") -Recurse -Force
& $jarTool --create --file (Join-Path $out "client-proof.jar") -C (Join-Path $out "classes") .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

Copy-Item -LiteralPath (Join-Path $out "client-proof.jar") -Destination (Join-Path $out "mods\client-proof.jar")
Write-Host "CLIENT PROOF BUILD ok=true jar=$out\client-proof.jar mods=$out\mods"
