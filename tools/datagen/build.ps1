$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdk = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1'))[0]
if (-not $jdk) { $jdk = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Java\jdk-26.0.1" } }
$javac = Join-Path $jdk "bin\javac.exe"
if (-not (Test-Path $javac)) { throw "No JDK 26 found. Set JAVA_HOME." }

$out = Join-Path $root "build\classes"
if (Test-Path $out) { Remove-Item -LiteralPath $out -Recurse -Force }
New-Item -ItemType Directory -Path $out -Force | Out-Null
$sources = Get-ChildItem -LiteralPath (Join-Path $root "src") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
& $javac --release 26 -encoding UTF-8 -d $out $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }
Write-Host "DATAGEN BUILD ok=true classes=$out"
