$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdk = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1'))[0]
if (-not $jdk) { $jdk = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Java\jdk-26.0.1" } }
$java = Join-Path $jdk "bin\java.exe"
if (-not (Test-Path $java)) { throw "No JDK 26 found. Set JAVA_HOME." }

& (Join-Path $root "build.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $java -cp (Join-Path $root "build\classes") weaver.datagen.DatagenSelfTest
exit $LASTEXITCODE
