param(
    [string]$Name = "Player",
    # Join a server at this address (for example 192.168.1.20:25565) instead of the title screen.
    [string]$Server = "",
    # For checking the package: start, wait until the game is up, then close it.
    [switch]$Check
)
$ErrorActionPreference = "Stop"

# Starts Minecraft 26.3 with Weaver and the mods in .\mods.

$root = $PSScriptRoot
$game = Join-Path $root "game"
if (-not (Test-Path (Join-Path $game "classpath.txt"))) {
    Write-Host "The game is not downloaded yet. Run first:  powershell -ExecutionPolicy Bypass -File get-game.ps1"
    Write-Host "اللعبة غير منزّلة بعد. شغّل أولاً:  powershell -ExecutionPolicy Bypass -File get-game.ps1"
    exit 1
}
$jdk = @(& (Join-Path $root "tools\jdk.ps1")) | Select-Object -First 1
if (-not $jdk) {
    Write-Host "Java 26 (JDK) is needed: install it from https://adoptium.net/ or https://jdk.java.net/26/ and run play.ps1 again."
    Write-Host "يلزم Java 26 (JDK): ثبّته من https://adoptium.net/ أو https://jdk.java.net/26/ ثم شغّل play.ps1 مرة أخرى."
    exit 1
}
if ($Name -notmatch '^[A-Za-z0-9_]{3,16}$') { throw "Name must be 3-16 letters, digits, or _ (like a Minecraft name)" }

# The offline UUID Minecraft derives from a name, so each player is distinct on a server.
$md5 = [System.Security.Cryptography.MD5]::Create().ComputeHash([System.Text.Encoding]::UTF8.GetBytes("OfflinePlayer:$Name"))
$md5[6] = ($md5[6] -band 0x0f) -bor 0x30
$md5[8] = ($md5[8] -band 0x3f) -bor 0x80
$hex = -join ($md5 | ForEach-Object { $_.ToString("x2") })
$uuid = "$($hex.Substring(0,8))-$($hex.Substring(8,4))-$($hex.Substring(12,4))-$($hex.Substring(16,4))-$($hex.Substring(20,12))"

$logs = Join-Path $root "logs"
New-Item -ItemType Directory -Path $logs -Force | Out-Null
$arguments = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $root "tools\launcher\launch.ps1"),
    "-Side", "client", "-GameDir", $game, "-AgentJar", (Join-Path $root "weaver\agent\weaver-agent.jar"),
    "-ModsDir", (Join-Path $root "mods"), "-ConfigDir", (Join-Path $root "weaver\config"),
    "-DataDir", (Join-Path $root "weaver\data"), "-Log", (Join-Path $logs "client.log"),
    "-Markers", (Join-Path $logs "markers.log"), "-Username", $Name, "-Uuid", $uuid)
if ($Server) { $arguments += @("-QuickPlayServer", $Server) }
if ($Check) {
    $arguments += @("-TimeoutSeconds", "240", "-WaitFor", "Sound engine started")
} else {
    $arguments += @("-Interactive", "-Visible")
}
$env:WEAVER_JDK = $jdk
Write-Host "Starting Minecraft 26.3 with Weaver as $Name (Java: $jdk)"
& powershell @arguments
exit $LASTEXITCODE
