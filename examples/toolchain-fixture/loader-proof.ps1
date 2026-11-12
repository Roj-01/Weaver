param(
    [string]$MainJar = "",
    [string]$ExportDir = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$agentJar = Join-Path $repo "core\build\agent\weaver-agent.jar"
$agentLib = Join-Path $repo "core\build\agent\lib"

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + $candidates + @("C:\Program Files\Java\jdk-26.0.1")
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath (Join-Path $candidate "bin\javac.exe")) {
            return $candidate
        }
    }
    throw "No JDK 26 found. Set JAVA_HOME."
}

if (-not $MainJar) {
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $root "build\libs") -Filter "*.jar" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } |
        Sort-Object LastWriteTime -Descending)
    if ($jars.Count -eq 0) { throw "no fixture jar found; run build.ps1 first" }
    $MainJar = $jars[0].FullName
}
if (-not $ExportDir) {
    $ExportDir = Join-Path $root "build\weaver\mods"
}

if (-not (Test-Path -LiteralPath $MainJar)) { throw "main jar is missing: $MainJar" }
if (-not (Test-Path -LiteralPath $ExportDir)) { throw "jij export directory is missing: $ExportDir (run build.ps1 first)" }
if (-not (Test-Path -LiteralPath $agentJar)) { throw "agent jar is missing: $agentJar" }

$jdk = Find-Jdk
$javac = Join-Path $jdk "bin\javac.exe"
$java = Join-Path $jdk "bin\java.exe"

$libraryJars = @(Get-ChildItem -LiteralPath $agentLib -Filter "*.jar" -File -ErrorAction SilentlyContinue |
    ForEach-Object { $_.FullName })
$classesDir = Join-Path $root "build\loader-proof\classes"
if (Test-Path -LiteralPath $classesDir) {
    Remove-Item -LiteralPath $classesDir -Recurse -Force
}
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

$source = Join-Path $root "loader\LoaderProof.java"
$compileClassPath = (@($agentJar) + $libraryJars) -join ";"
& $javac "--release" "26" "-encoding" "UTF-8" "-cp" $compileClassPath "-d" $classesDir $source
if ($LASTEXITCODE -ne 0) { throw "LoaderProof javac failed with exit code $LASTEXITCODE" }

$runClassPath = (@($agentJar) + $libraryJars + @($classesDir)) -join ";"
& $java "-cp" $runClassPath "LoaderProof" $MainJar $ExportDir
if ($LASTEXITCODE -ne 0) {
    Write-Host "TOOLCHAINFIXTURE LOADER ok=false exit=$LASTEXITCODE"
    exit $LASTEXITCODE
}
exit 0
