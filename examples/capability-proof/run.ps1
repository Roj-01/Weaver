$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path $root "out"

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + $candidates + @("C:\Program Files\Java\jdk-26.0.1")
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "bin\java.exe")) { return $candidate }
    }
    throw "No JDK 26 found. Set JAVA_HOME to a JDK 26 installation."
}

& (Join-Path $root "build.ps1")
if ($LASTEXITCODE -ne 0) { throw "build.ps1 failed with exit code $LASTEXITCODE" }

$jdk = Find-Jdk
$java = Join-Path $jdk "bin\java.exe"
$classpathFile = Join-Path $out "run-classpath.txt"
if (-not (Test-Path -LiteralPath $classpathFile)) {
    throw "run classpath file missing: $classpathFile"
}
$classpath = (Get-Content -LiteralPath $classpathFile -Raw).Trim()

$exitCode = 0
Push-Location $root
try {
    & $java -cp $classpath net.weaver.capabilityproof.CapabilityProof
    $exitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}

if ($exitCode -ne 0) {
    [Console]::Error.WriteLine("CapabilityProof failed with exit code $exitCode")
    exit $exitCode
}
exit 0
