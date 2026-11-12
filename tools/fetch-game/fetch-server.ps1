param(
    [string]$GameDir = "",
    [string]$ServerDir = "",
    [string]$Version = "26.3"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $GameDir) { $GameDir = Join-Path $root "work\game-$Version" }
if (-not $ServerDir) { $ServerDir = Join-Path $root "work\server-$Version" }

$versionJson = Join-Path $GameDir "version.json"
if (-not (Test-Path $versionJson)) {
    throw "missing $versionJson; run fetch-game.sh first"
}
$meta = Get-Content -LiteralPath $versionJson -Raw | ConvertFrom-Json
$url = $meta.downloads.server.url
$sha1 = $meta.downloads.server.sha1
$target = Join-Path $ServerDir "server.jar"
New-Item -ItemType Directory -Path $ServerDir -Force | Out-Null

Write-Host "SERVER FETCH url=$url target=$target"
if (-not (Test-Path $target) -or (Get-FileHash -Algorithm SHA1 -LiteralPath $target).Hash.ToLower() -ne $sha1.ToLower()) {
    Invoke-WebRequest -Uri $url -OutFile $target
}
$got = (Get-FileHash -Algorithm SHA1 -LiteralPath $target).Hash.ToLower()
if ($got -ne $sha1.ToLower()) {
    throw "server.jar sha1 mismatch: $got != $sha1"
}
Set-Content -LiteralPath (Join-Path $ServerDir "eula.txt") -Value "eula=true" -Encoding ASCII

$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }
$log = Join-Path $ServerDir "fetch-server.log"
$process = Start-Process -FilePath $java -ArgumentList @("-jar", "server.jar", "nogui") `
    -WorkingDirectory $ServerDir -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
    -PassThru -NoNewWindow

$deadline = (Get-Date).AddSeconds(30)
$realJar = Join-Path $ServerDir "versions\$Version\server-$Version.jar"
while ((Get-Date) -lt $deadline) {
    if (Test-Path $realJar) { break }
    if ($process.HasExited) { break }
    Start-Sleep -Milliseconds 500
}
if (-not $process.HasExited) {
    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
}

if (Test-Path $realJar) {
    Write-Host "SERVER FETCH ok=true jar=$realJar"
} else {
    Write-Host "SERVER FETCH ok=false jar=$realJar log=$log"
    if (Test-Path $log) { Get-Content -LiteralPath $log -Tail 40 }
    exit 1
}
