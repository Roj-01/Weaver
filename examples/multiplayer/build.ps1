$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$gameDir = if ($env:WEAVER_GAME_DIR) { $env:WEAVER_GAME_DIR } else { Join-Path $repo "work\game-26.3" }
$agentLib = Join-Path $repo "core\build\agent\lib"

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
$compileCp = "$gameCp;$libCp;$($apiGameJar.FullName)"

if (Test-Path $out) { Remove-Item -LiteralPath $out -Recurse -Force }
New-Item -ItemType Directory -Path (Join-Path $out "classes") | Out-Null

$sources = Get-ChildItem -LiteralPath (Join-Path $root "src\main\java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
if ($sources.Count -eq 0) { throw "No Java sources" }

& $javac --release 26 -encoding UTF-8 -cp $compileCp -d (Join-Path $out "classes") $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }
Copy-Item -Path (Join-Path $root "src\main\resources\*") -Destination (Join-Path $out "classes") -Recurse -Force


# The same mod for the server and the matching client, the mod at another
# version for the version check, and no mod for the missing-mod check.
$jar = Join-Path $out "weavermp.jar"
& $jarTool --create --file $jar -C (Join-Path $out "classes") .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
foreach ($dir in @("mods-server", "mods-client", "mods-client-v101", "mods-none")) {
    New-Item -ItemType Directory -Path (Join-Path $out $dir) -Force | Out-Null
}
Copy-Item -LiteralPath $jar -Destination (Join-Path $out "mods-server\weavermp.jar")
Copy-Item -LiteralPath $jar -Destination (Join-Path $out "mods-client\weavermp.jar")

$v101 = Join-Path $out "classes-v101"
Copy-Item -LiteralPath (Join-Path $out "classes") -Destination $v101 -Recurse
$descriptor = Join-Path $v101 "weaver.mod.json"
(Get-Content -LiteralPath $descriptor -Raw).Replace('"version": "1.0.0"', '"version": "1.0.1"') |
    Set-Content -LiteralPath $descriptor -Encoding utf8 -NoNewline
& $jarTool --create --file (Join-Path $out "mods-client-v101\weavermp.jar") -C $v101 .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
Write-Host "MP BUILD ok=true out=$out"
