$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$classes = Join-Path $root "build\classes"
if (-not (Test-Path $classes)) {
    & (Join-Path $root "build.ps1")
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
$jdk = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1'))[0]
if (-not $jdk) { $jdk = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Java\jdk-26.0.1" } }
$java = Join-Path $jdk "bin\java.exe"
& $java -cp $classes weaver.datagen.Main @args
exit $LASTEXITCODE
