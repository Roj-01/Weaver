# Runs the vanilla client data generator (net.minecraft.client.data.Main --client)
# under the Weaver agent with the client proof mod, and checks that Weaver fired
# InitializeClientRegistriesEvent. Prints CLIENT DATAGEN PROOF ok=true on success.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$here = $PSScriptRoot
$out = Join-Path $here "out"
$game = Join-Path $root "work\game-26.3"
$agent = Join-Path $root "core\build\agent\weaver-agent.jar"
$java = Join-Path (& (Join-Path $root "tools\jdk.ps1")) "bin\java.exe"
if (-not (Test-Path $agent)) { throw "build the agent first (gradlew :core:agentJar)" }
$logs = Join-Path $out "logs"
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$generated = Join-Path $out "datagen-output"
if (Test-Path $generated) { Remove-Item -Recurse -Force $generated }
$log = Join-Path $logs "datagen.log"
$classpath = (Get-Content -Raw (Join-Path $game "classpath.txt")).Trim()
$agentArg = "-javaagent:$agent=side=client;integratedServer=false;modsDir=$(Join-Path $out 'mods');configDir=$(Join-Path $out 'datagen-config');dataDir=$(Join-Path $out 'datagen-data')"
$arguments = @("-Xmx2G", "-XX:StackShadowPages=32", "--enable-native-access=ALL-UNNAMED", "-cp", $classpath, $agentArg,
    "net.minecraft.client.data.Main", "--client", "--output", $generated)
$quoted = @($arguments | ForEach-Object { if ($_ -match '\s' -and $_ -notmatch '"') { '"' + $_ + '"' } else { $_ } })
$process = Start-Process -FilePath $java -ArgumentList $quoted -WorkingDirectory $game -RedirectStandardOutput $log `
    -RedirectStandardError "$log.err" -PassThru -NoNewWindow
# Windows PowerShell only reports ExitCode when the handle was read while the process ran.
$null = $process.Handle
if (-not $process.WaitForExit(600000)) {
    Stop-Process -Id $process.Id -Force
    Write-Host "CLIENT DATAGEN PROOF ok=false reason=timeout log=$log"
    exit 1
}
$text = Get-Content -Raw $log
$fired = $text -match '\[weaver\] CLIENT DATAGEN initialize client=true'
$models = Test-Path (Join-Path $generated "assets\minecraft\models")
$ok = $fired -and $models -and $process.ExitCode -eq 0
Write-Host "CLIENT DATAGEN PROOF ok=$($ok.ToString().ToLower()) fired=$fired models=$models exit=$($process.ExitCode) log=$log"
if (-not $ok) { exit 1 }
