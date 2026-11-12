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

if (Test-Path $out) { Remove-Item -LiteralPath $out -Recurse -Force }
New-Item -ItemType Directory -Path $out -Force | Out-Null

$datagenBuild = Join-Path $repo "tools\datagen\build.ps1"
$datagenRun = Join-Path $repo "tools\datagen\run.ps1"
& powershell -NoProfile -ExecutionPolicy Bypass -File $datagenBuild
if ($LASTEXITCODE -ne 0) { throw "datagen build failed" }
& powershell -NoProfile -ExecutionPolicy Bypass -File $datagenRun --manifest (Join-Path $root "datagen.json") --out (Join-Path $out "pack") --verify --fingerprint
if ($LASTEXITCODE -ne 0) { throw "datagen run failed" }

$pack = Join-Path $out "pack"
$serverPack = Join-Path $out "server-pack"
if (Test-Path $serverPack) { Remove-Item -LiteralPath $serverPack -Recurse -Force }
Copy-Item -LiteralPath $pack -Destination $serverPack -Recurse -Force
Copy-Item -LiteralPath (Join-Path $pack "pack.data.mcmeta") -Destination (Join-Path $serverPack "pack.mcmeta") -Force

$gameCp = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
$libCp = (Get-ChildItem -LiteralPath $agentLib -Filter *.jar | ForEach-Object { $_.FullName }) -join ";"

$apiJar = Get-ChildItem -LiteralPath (Join-Path $repo "api\build\libs") -Filter "api-*.jar" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $apiJar) {
    $apiJar = Get-ChildItem -LiteralPath $agentLib -Filter "api-*.jar" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}
if (-not $apiJar) { throw "api jar missing: run .\gradlew.bat :api:jar" }

$apiGameJar = Get-ChildItem -LiteralPath (Join-Path $repo "api-game\build\libs") -Filter "api-game-*.jar" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $apiGameJar) {
    $apiGameJar = Get-ChildItem -LiteralPath $agentLib -Filter "api-game-*.jar" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}
if (-not $apiGameJar) { throw "api-game jar missing: run .\gradlew.bat :api-game:jar" }

$compileCp = "$gameCp;$libCp;$($apiJar.FullName);$($apiGameJar.FullName);$agentJar"

New-Item -ItemType Directory -Path (Join-Path $out "classes") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $out "mods") | Out-Null

$sources = Get-ChildItem -LiteralPath (Join-Path $root "src\main\java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
if ($sources.Count -eq 0) { throw "No Java sources" }

& $javac --release 26 -encoding UTF-8 -cp $compileCp -d (Join-Path $out "classes") $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Copy-Item -Path (Join-Path $root "src\main\resources\*") -Destination (Join-Path $out "classes") -Recurse -Force
& $jarTool --create --file (Join-Path $out "datagen-proof.jar") -C (Join-Path $out "classes") .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

Copy-Item -LiteralPath (Join-Path $out "datagen-proof.jar") -Destination (Join-Path $out "mods\datagen-proof.jar")
Write-Host "DATAGEN PROOF BUILD ok=true jar=$out\datagen-proof.jar mods=$out\mods"
