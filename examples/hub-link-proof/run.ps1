$ErrorActionPreference = "Stop"

# Weaver Hub requests from a real client (the client runs with -Dweaver.hub=true, as the Hub starts it):
#  1. a vanilla server (no agent) + a client carrying a both-sides mod -> "vanilla-server" request,
#     and the client leaves with the Hub message;
#  2. a Weaver server with a mod + a client without it -> "server-mods" request naming the missing mod.
# Needs examples/proof built (its proof.jar is the both-sides mod) and the agent jar.

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$launcher = Join-Path $repo "tools\launcher\launch.ps1"
$agent = Join-Path $repo "core\build\agent\weaver-agent.jar"
$proofMods = Join-Path $repo "examples\proof\out\mods"
$serverDir = if ($env:WEAVER_SERVER_DIR) { $env:WEAVER_SERVER_DIR } else { Join-Path $repo "work\server-26.3" }
$gameDir = if ($env:WEAVER_GAME_DIR) { $env:WEAVER_GAME_DIR } else { Join-Path $repo "work\game-26.3" }
if (-not (Test-Path (Join-Path $proofMods "proof.jar"))) { throw "build examples/proof first" }
if (-not (Test-Path $agent)) { throw "agent missing: run .\gradlew.bat :core:agentJar" }

$jdk = @(& (Join-Path $repo "tools\jdk.ps1")) + @($env:JAVA_HOME) |
    Where-Object { $_ -and (Test-Path (Join-Path $_ "bin\java.exe")) } | Select-Object -First 1
$java = Join-Path $jdk "bin\java.exe"

if (Test-Path $out) { Remove-Item -LiteralPath $out -Recurse -Force }
New-Item -ItemType Directory -Path $out, (Join-Path $out "empty-mods") | Out-Null
$failures = @()

function Stop-Port([int]$port) {
    $owner = (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).OwningProcess
    foreach ($p in @($owner)) { if ($p) { & taskkill /PID $p /T /F | Out-Null } }
}

function Wait-Line([string]$file, [string]$pattern, [int]$seconds) {
    $end = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $end) {
        if ((Test-Path $file) -and (Select-String -LiteralPath $file -Pattern $pattern -SimpleMatch -Quiet)) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Run-Client([string]$name, [string]$mods, [string]$server, [string]$waitFor) {
    $dir = Join-Path $out $name
    New-Item -ItemType Directory -Path $dir | Out-Null
    $env:JAVA_TOOL_OPTIONS = "-Dweaver.hub=true"
    try {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $launcher -Side client -GameDir $gameDir -AgentJar $agent `
            -ModsDir $mods -ConfigDir (Join-Path $dir "config") -DataDir (Join-Path $dir "data") `
            -Log (Join-Path $dir "client.log") -Markers (Join-Path $dir "markers.log") `
            -QuickPlayServer $server -TimeoutSeconds 300 -WaitFor $waitFor | Out-Null
    } finally {
        Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
    }
    return Join-Path $dir "client.log"
}

function Hub-Line([string]$log) {
    $line = Select-String -LiteralPath $log -Pattern "[STDOUT]: [weaver-hub] " -SimpleMatch | Select-Object -First 1
    if (-not $line) { return $null }
    $json = $line.Line.Substring($line.Line.IndexOf("[weaver-hub] ") + 13)
    return $json | ConvertFrom-Json
}

# 1. vanilla server
$vanillaPort = 25570
$vanillaLog = Join-Path $out "vanilla-server.log"
$cp = ((Get-ChildItem -LiteralPath (Join-Path $serverDir "libraries") -Recurse -Filter *.jar | ForEach-Object { $_.FullName }) +
    @(Join-Path $serverDir "versions\26.3\server-26.3.jar")) -join ";"
$vanilla = Start-Process -FilePath $java -ArgumentList @("-Xmx2G", "-cp", "`"$cp`"", "net.minecraft.server.Main", "nogui", "--port", "$vanillaPort") `
    -WorkingDirectory $serverDir -RedirectStandardOutput $vanillaLog -RedirectStandardError (Join-Path $out "vanilla-server.err") -PassThru -WindowStyle Hidden
try {
    if (-not (Wait-Line $vanillaLog "Done (" 120)) { throw "vanilla server did not start" }
    $log = Run-Client "vanilla-client" $proofMods "localhost:$vanillaPort" "HUB LINK vanilla server"
    $request = Hub-Line $log
    $ok = $request -and $request.type -eq "vanilla-server" -and $request.server -eq "localhost:$vanillaPort" -and ($request.paused -contains "weaverproof")
    $left = Select-String -LiteralPath $log -Pattern "Weaver Hub is restarting the game with only your client-side mods" -SimpleMatch -Quiet
    Write-Host "HUB LINK CHECK ok=$([bool]$ok) vanilla-server request paused=$($request.paused -join ',')"
    Write-Host "HUB LINK CHECK ok=$([bool]$left) client left the vanilla server with the Hub message"
    if (-not $ok) { $failures += "vanilla request" }
    if (-not $left) { $failures += "vanilla disconnect" }
} finally {
    Stop-Port $vanillaPort
    if (-not $vanilla.HasExited) { $vanilla.Kill() }
}

# 2. Weaver server with the proof mod, client without it
$weaverPort = 25568
$serverLog = Join-Path $out "weaver-server.log"
$server = Start-Process -FilePath "powershell" -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$launcher`"",
    "-Side", "server", "-ServerDir", "`"$serverDir`"", "-AgentJar", "`"$agent`"", "-ModsDir", "`"$proofMods`"",
    "-ConfigDir", "`"$(Join-Path $out 'server-config')`"", "-DataDir", "`"$(Join-Path $out 'server-data')`"",
    "-Log", "`"$serverLog`"", "-Markers", "`"$(Join-Path $out 'server-markers.log')`"", "-Port", "$weaverPort",
    "-TimeoutSeconds", "400", "-WaitFor", "NEVER-PRINTED-MARKER") -PassThru -WindowStyle Hidden
try {
    if (-not (Wait-Line $serverLog "Done (" 180)) { throw "Weaver server did not start" }
    $log = Run-Client "weaver-client" (Join-Path $out "empty-mods") "localhost:$weaverPort" "Client disconnected with reason"
    $request = Hub-Line $log
    $ok = $request -and $request.type -eq "server-mods" -and ($request.missing | Where-Object { $_.id -eq "weaverproof" })
    Write-Host "HUB LINK CHECK ok=$([bool]$ok) server-mods request missing=$(($request.missing | ForEach-Object { $_.id }) -join ',')"
    if (-not $ok) { $failures += "server-mods request" }
} finally {
    Stop-Port $weaverPort
    if (-not $server.HasExited) { & taskkill /PID $server.Id /T /F | Out-Null }
}

if ($failures.Count -eq 0) {
    Write-Host "HUB LINK PROOF RESULT ok=true"
} else {
    Write-Host "HUB LINK PROOF RESULT ok=false failed=$($failures -join '|')"
    exit 1
}
