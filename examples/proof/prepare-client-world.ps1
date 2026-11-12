[CmdletBinding()]
param(
    [string]$Source = "",
    [string]$Destination = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$gameDir = if ($env:WEAVER_GAME_DIR) { $env:WEAVER_GAME_DIR } else { Join-Path $repo "work\game-26.3" }

if (-not $Source) { $Source = Join-Path $gameDir "saves\New World (2)" }
if (-not $Destination) { $Destination = Join-Path $root "fixture-world" }

if (-not (Test-Path -LiteralPath (Join-Path $Source "level.dat") -PathType Leaf)) {
    throw "source world has no level.dat: $Source"
}

if (Test-Path -LiteralPath $Destination) {
    Remove-Item -LiteralPath $Destination -Recurse -Force
}
New-Item -ItemType Directory -Path $Destination -Force | Out-Null

Get-ChildItem -LiteralPath $Source -Force |
    Where-Object { $_.Name -ne "session.lock" } |
    ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $Destination -Recurse -Force
    }
Remove-Item -LiteralPath (Join-Path $Destination "session.lock") -Force -ErrorAction SilentlyContinue

Write-Host "CLIENT FIXTURE ok=true source=$Source destination=$Destination"
