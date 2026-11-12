param(
    [string]$Tests = "",
    [int]$TimeoutSeconds = 300
)
$ErrorActionPreference = "Stop"

# Runs the mods in out\mods with vanilla's game test server
# (net.minecraft.gametest.Main) and the Weaver agent, then reads the JUnit
# report. Exit code: 0 when every required test passed.

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$run = Join-Path $out "run"
$logs = Join-Path $out "logs"
$agent = Join-Path $repo "core\build\agent\weaver-agent.jar"
$serverDir = if ($env:WEAVER_SERVER_DIR) { $env:WEAVER_SERVER_DIR } else { Join-Path $repo "work\server-26.3" }
$report = Join-Path $out "gametest-report.xml"
$log = Join-Path $logs "gametest.log"

if (-not (Test-Path (Join-Path $out "mods"))) { throw "run build.ps1 first" }
if (-not (Test-Path $agent)) { throw "agent jar missing: $agent (run .\gradlew.bat :core:agentJar)" }
$serverJar = Join-Path $serverDir "versions\26.3\server-26.3.jar"
if (-not (Test-Path $serverJar)) { throw "server jar missing: $serverJar" }

$jdkHome = @(& (Join-Path $repo "tools\jdk.ps1")) + @($env:JAVA_HOME) |
    Where-Object { $_ -and (Test-Path (Join-Path $_ "bin\java.exe")) } | Select-Object -First 1
if (-not $jdkHome) { throw "No JDK 26 found. Set JAVA_HOME." }
$java = Join-Path $jdkHome "bin\java.exe"

foreach ($dir in @($run, $logs)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
# The game test server creates its world under the universe folder; start clean.
$universe = Join-Path $run "universe"
if (Test-Path $universe) { Remove-Item -LiteralPath $universe -Recurse -Force }
Remove-Item -LiteralPath $report, $log, "$log.err" -Force -ErrorAction SilentlyContinue

$libraries = Get-ChildItem -LiteralPath (Join-Path $serverDir "libraries") -Recurse -Filter *.jar |
    ForEach-Object { $_.FullName } | Sort-Object
$classpath = (@($libraries) + @($serverJar)) -join ";"
$agentArg = "-javaagent:$agent=side=server;integratedServer=false;modsDir=$(Join-Path $out 'mods');configDir=$(Join-Path $out 'config');dataDir=$(Join-Path $out 'data');markersFile=$(Join-Path $logs 'markers.log')"
$arguments = @("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Xmx2G", "--enable-native-access=ALL-UNNAMED",
    "-cp", $classpath, $agentArg, "net.minecraft.gametest.Main",
    "--universe", $universe, "--report", $report)
if ($Tests) { $arguments += @("--tests", $Tests) }

Write-Host "GAMETEST START report=$report log=$log"
$process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $run `
    -RedirectStandardOutput $log -RedirectStandardError "$log.err" -PassThru -NoNewWindow
# Reading the handle now keeps the exit code available after the process ends.
$null = $process.Handle
if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    Write-Host "GAMETEST RESULT ok=false reason=timeout log=$log"
    exit 1
}
$exit = $process.ExitCode
if (-not (Test-Path $report)) {
    Write-Host "GAMETEST RESULT ok=false reason=no-report exit=$exit log=$log"
    if (Test-Path $log) { Get-Content -LiteralPath $log -Tail 60 }
    exit 1
}
[xml]$xml = Get-Content -LiteralPath $report -Raw
$cases = @($xml.SelectNodes("//testcase"))
$failed = @($cases | Where-Object { $_.SelectSingleNode("failure") -ne $null })
$skipped = @($cases | Where-Object { $_.SelectSingleNode("skipped") -ne $null })
foreach ($case in $cases) {
    # Vanilla reports a failed optional test as skipped.
    $state = if ($case.SelectSingleNode("failure")) { "failed" } elseif ($case.SelectSingleNode("skipped")) { "optional-failed" } else { "passed" }
    Write-Host "GAMETEST CASE name=$($case.name) result=$state"
}
$ok = $exit -eq 0
Write-Host "GAMETEST RESULT ok=$($ok.ToString().ToLower()) exit=$exit tests=$($cases.Count) failed=$($failed.Count) skipped=$($skipped.Count) report=$report"
if ($ok) { exit 0 } else { exit 1 }
