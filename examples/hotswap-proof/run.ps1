param([ValidateSet("server", "client")][string]$Side = "server")
$ErrorActionPreference = "Stop"

# Developer hot reload on a dedicated server, across an automatic restart: builds the proof
# mod (first version in the jar and in a watched class folder, second version beside it),
# starts the server with -HotSwapDirs and -HotSwapResources, and waits for HOTSWAP PROOF
# RESULT, which the mod prints after the restart its shape change causes.

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$agentLib = Join-Path $repo "core\build\agent\lib"
$agent = Join-Path $repo "core\build\agent\weaver-agent.jar"
$launcher = Join-Path $repo "tools\launcher\launch.ps1"
$serverDir = if ($env:WEAVER_SERVER_DIR) { $env:WEAVER_SERVER_DIR } else { Join-Path $repo "work\server-26.3" }
$port = if ($env:WEAVER_SERVER_PORT) { $env:WEAVER_SERVER_PORT } else { "25567" }

$jdk = @(& (Join-Path $repo "tools\jdk.ps1")) + @($env:JAVA_HOME) |
    Where-Object { $_ -and (Test-Path (Join-Path $_ "bin\javac.exe")) } | Select-Object -First 1
if (-not $jdk) { throw "No JDK 26 found. Set JAVA_HOME." }
$javac = Join-Path $jdk "bin\javac.exe"
$jarTool = Join-Path $jdk "bin\jar.exe"
if (-not (Test-Path $agent)) { throw "agent missing: run .\gradlew.bat :core:agentJar" }
$apiJar = Get-ChildItem -LiteralPath $agentLib -Filter "api-2*.jar" | Select-Object -First 1
if (-not $apiJar) { throw "api jar missing in $agentLib" }
$apiGameJar = Get-ChildItem -LiteralPath $agentLib -Filter "api-game-*.jar" | Select-Object -First 1
$gameDir = if ($env:WEAVER_GAME_DIR) { $env:WEAVER_GAME_DIR } else { Join-Path $repo "work\game-26.3" }
$gameCp = (Get-Content -LiteralPath (Join-Path $gameDir "classpath.txt") -Raw).Trim()
$compileCp = "$gameCp;$($apiJar.FullName);$($apiGameJar.FullName)"

if (Test-Path $out) { Remove-Item -LiteralPath $out -Recurse -Force }
$v1 = Join-Path $out "classes"
$watch = Join-Path $out "hotswap\watch"
$v2 = Join-Path $out "hotswap\v2"
$mods = Join-Path $out "mods"
$resources = Join-Path $out "hotswap\resources"
New-Item -ItemType Directory -Path $v1, $watch, $v2, $mods, $resources | Out-Null

$main = Get-ChildItem -LiteralPath (Join-Path $root "src\main\java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $javac --release 26 -encoding UTF-8 -cp $compileCp -d $v1 $main
if ($LASTEXITCODE -ne 0) { throw "javac (first version) failed" }
$second = Get-ChildItem -LiteralPath (Join-Path $root "src\v2\java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $javac --release 26 -encoding UTF-8 -d $v2 $second
if ($LASTEXITCODE -ne 0) { throw "javac (second version) failed" }
Copy-Item -Path (Join-Path $root "src\main\resources\*") -Destination $v1 -Recurse -Force
& $jarTool --create --file (Join-Path $mods "hotswapproof.jar") -C $v1 .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
# The watched folder starts as the build output the server was started from.
Copy-Item -Path (Join-Path $v1 "net") -Destination $watch -Recurse

$logs = Join-Path $out "logs"
New-Item -ItemType Directory -Path $logs | Out-Null
$log = Join-Path $logs "$Side.log"
$sideArguments = if ($Side -eq "server") {
    @("-Side", "server", "-ServerDir", $serverDir, "-Port", $port)
} else {
    # the client plays the fixture world; a restart must bring it straight back there
    $fixture = Join-Path $repo "examples\proof\fixture-world"
    $world = "WeaverHotSwapFixture"
    $target = Join-Path (Join-Path $gameDir "saves") $world
    if (Test-Path $target) { Remove-Item -LiteralPath $target -Recurse -Force }
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    Get-ChildItem -LiteralPath $fixture -Force | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $target -Recurse -Force }
    @("-Side", "client", "-GameDir", $gameDir, "-QuickPlayWorld", $world)
}
$arguments = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $launcher) + $sideArguments + @(
    "-AgentJar", $agent,
    "-ModsDir", $mods,
    "-ConfigDir", (Join-Path $out "config"),
    "-DataDir", (Join-Path $out "data"),
    "-Log", $log,
    "-Markers", (Join-Path $logs "markers.log"),
    "-TimeoutSeconds", "300",
    "-HotSwapDirs", $watch,
    "-HotSwapResources", $resources,
    "-WaitFor", "HOTSWAP PROOF RESULT"
)
& powershell @arguments
$result = if (Test-Path $log) { Select-String -LiteralPath $log -Pattern "HOTSWAP PROOF RESULT" | Select-Object -Last 1 } else { $null }
Select-String -LiteralPath $log -Pattern "HOTSWAP" -ErrorAction SilentlyContinue | ForEach-Object { $_.Line }
if (-not $result -or $result.Line -notmatch "ok=true") {
    Write-Host "HOTSWAP PROOF RUN ok=false log=$log"
    exit 1
}
if ($Side -eq "client") {
    # assets: phase 1 changed a language file; the client must have reloaded its resources
    $before = Get-ChildItem -LiteralPath $logs -Filter "$Side.log.before-restart-*" | Select-Object -First 1
    $assets = $before -and (Select-String -LiteralPath $before.FullName -Pattern "HOTSWAP ASSETS RELOADED ok=true" -SimpleMatch -Quiet)
    $back = Select-String -LiteralPath $log -Pattern "PROOF CHECK ok=true phase 1 before the restart" -SimpleMatch -Quiet
    Write-Host "HOTSWAP PROOF CHECK ok=$([bool]$assets) client assets reloaded live"
    Write-Host "HOTSWAP PROOF CHECK ok=$([bool]$back) client came back into the same world after the restart"
    if (-not $assets -or -not $back) { Write-Host "HOTSWAP PROOF RUN ok=false log=$log"; exit 1 }
}
Write-Host "HOTSWAP PROOF RUN ok=true log=$log"
