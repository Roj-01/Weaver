param(
    [int]$Port = 25570,
    [int]$Attempts = 3
)
$ErrorActionPreference = "Stop"

# Two-process multiplayer test: a dedicated server with weavermp and separate
# clients joining it over 127.0.0.1. One client with the same mod must join and
# see the registry check, data registries, a data map, a capability and a
# channel arrive over the network; three clients that do not match the server
# must be refused with the right reason.

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$logs = Join-Path $out "logs"
$launcher = Join-Path $repo "tools\launcher\launch.ps1"
$agent = Join-Path $repo "core\build\agent\weaver-agent.jar"
$serverDir = if ($env:WEAVER_SERVER_DIR) { $env:WEAVER_SERVER_DIR } else { Join-Path $repo "work\server-26.3" }
$gameDir = if ($env:WEAVER_GAME_DIR) { $env:WEAVER_GAME_DIR } else { Join-Path $repo "work\game-26.3" }
if (-not (Test-Path (Join-Path $out "mods-server"))) { throw "run build.ps1 first" }
New-Item -ItemType Directory -Path $logs -Force | Out-Null

$world = "weaver-mp"
$worldDir = Join-Path $serverDir $world
if (Test-Path $worldDir) { Remove-Item -LiteralPath $worldDir -Recurse -Force }
$serverLog = Join-Path $logs "server.log"

Write-Host "MP SERVER START port=$Port world=$world"
# Start the launcher as its own process with its output in a file. The server it
# starts keeps running and inherits the launcher's handles; with `&` this script
# would read them through a pipe and wait for the server to exit.
$launchOut = Join-Path $logs "server-launch.txt"
$launchArgs = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$launcher`"", "-Side", "server",
    "-ServerDir", "`"$serverDir`"", "-AgentJar", "`"$agent`"", "-ModsDir", "`"$(Join-Path $out 'mods-server')`"",
    "-ConfigDir", "`"$(Join-Path $out 'server-config')`"", "-DataDir", "`"$(Join-Path $out 'server-data')`"",
    "-Log", "`"$serverLog`"", "-Markers", "`"$(Join-Path $logs 'server-markers.log')`"", "-Port", "$Port",
    "-World", $world, "-KeepRunning", "-TimeoutSeconds", "240", "-WaitFor", "`"Done \(`"")
$launch = Start-Process -FilePath "powershell" -ArgumentList $launchArgs -RedirectStandardOutput $launchOut `
    -RedirectStandardError "$launchOut.err" -WindowStyle Hidden -PassThru
$null = $launch.Handle
$launch.WaitForExit()
$serverOutput = @(Get-Content -LiteralPath $launchOut)
$serverOutput | ForEach-Object { Write-Host $_ }
$pidLine = @($serverOutput | Where-Object { $_ -match "LAUNCH RESULT side=server ok=true .*pid=(\d+)" }) | Select-Object -First 1
if (-not $pidLine) { Write-Host "MP RESULT ok=false reason=server-did-not-start log=$serverLog"; exit 1 }
$serverPid = [int]([regex]::Match($pidLine, "pid=(\d+)").Groups[1].Value)

function Invoke-Client([string]$Name, [string]$Mods, [string]$WaitFor, [hashtable]$Environment) {
    $log = Join-Path $logs "client-$Name.log"
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        foreach ($key in $Environment.Keys) { Set-Item -Path "Env:$key" -Value $Environment[$key] }
        try {
            & powershell -NoProfile -ExecutionPolicy Bypass -File $launcher -Side client -GameDir $gameDir `
                -AgentJar $agent -ModsDir $Mods -ConfigDir (Join-Path $out "client-config-$Name") `
                -DataDir (Join-Path $out "client-data-$Name") -Log $log `
                -Markers (Join-Path $logs "client-$Name-markers.log") -QuickPlayServer "127.0.0.1:$Port" `
                -TimeoutSeconds 180 -WaitFor $WaitFor *> (Join-Path $logs "client-$Name-launch.txt")
        } finally {
            foreach ($key in $Environment.Keys) { Remove-Item -Path "Env:$key" -ErrorAction SilentlyContinue }
        }
        $text = if (Test-Path $log) { Get-Content -LiteralPath $log -Raw } else { "" }
        if ($text -match $WaitFor) {
            return [pscustomobject]@{ Name = $Name; Found = $true; Text = $text; Log = $log; Attempt = $attempt }
        }
        Write-Host "MP CLIENT $Name attempt=$attempt ended without '$WaitFor' (the client can crash natively here); retrying"
    }
    return [pscustomobject]@{ Name = $Name; Found = $false; Text = $text; Log = $log; Attempt = $Attempts }
}

function Line([string]$Text, [string]$Pattern) {
    $match = [regex]::Match($Text, "[^\r\n]*" + $Pattern + "[^\r\n]*")
    if ($match.Success) { return $match.Value.Trim() } else { return "" }
}

$results = [ordered]@{}
try {
    # 1. Same mods: everything arrives over the network.
    $match = Invoke-Client "match" (Join-Path $out "mods-client") "MP CLIENT RESULT ok=" @{}
    $result = Line $match.Text "MP CLIENT RESULT ok="
    $checkAt = $match.Text.IndexOf("REGISTRY CHECK ok=true")
    $playAt = $match.Text.IndexOf("NETWORK BRIDGE phase side=CLIENT from=CONFIGURATION to=PLAY")
    $beforePlay = $checkAt -ge 0 -and $playAt -gt $checkAt
    $remoteSync = $match.Text.Contains("DATA MAPS synced") -and $match.Text.Contains("DATA REGISTRIES synced")
    $ok = $match.Found -and $result.Contains("ok=true") -and $beforePlay -and $remoteSync
    $results["match"] = $ok
    Write-Host "MP CHECK match ok=$($ok.ToString().ToLower()) beforePlay=$beforePlay remoteSync=$remoteSync $result"

    # 2. One extra item on the client: the registry check refuses the join.
    $extra = Invoke-Client "extra-item" (Join-Path $out "mods-client") "REGISTRY CHECK ok=false" @{ WEAVER_MP_EXTRA_ITEM = "1" }
    $reason = Line $extra.Text "REGISTRY CHECK ok=false"
    $ok = $extra.Found -and $reason.Contains("not on the server weavermp:extra")
    $results["extra-item"] = $ok
    Write-Host "MP CHECK extra-item ok=$($ok.ToString().ToLower()) $reason"

    # 3. Another version of the mod: the handshake refuses the join.
    $version = Invoke-Client "version" (Join-Path $out "mods-client-v101") "mod version mismatch" @{}
    $reason = Line $version.Text "mod version mismatch"
    $ok = $version.Found -and $reason.Contains("weavermp")
    $results["version"] = $ok
    Write-Host "MP CHECK version ok=$($ok.ToString().ToLower()) $reason"

    # 4. No mod on the client: the handshake refuses the join.
    $missing = Invoke-Client "missing" (Join-Path $out "mods-none") "missing required mod" @{}
    $reason = Line $missing.Text "missing required mod"
    $ok = $missing.Found -and $reason.Contains("weavermp")
    $results["missing"] = $ok
    Write-Host "MP CHECK missing ok=$($ok.ToString().ToLower()) $reason"
} finally {
    Stop-Process -Id $serverPid -Force -ErrorAction SilentlyContinue
}

$all = @($results.Values | Where-Object { -not $_ }).Count -eq 0 -and $results.Count -eq 4
$summary = ($results.Keys | ForEach-Object { "$_=$($results[$_].ToString().ToLower())" }) -join " "
Write-Host "MP RESULT ok=$($all.ToString().ToLower()) $summary logs=$logs"
if ($all) { exit 0 } else { exit 1 }
