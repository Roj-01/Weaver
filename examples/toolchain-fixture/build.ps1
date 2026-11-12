param(
    [switch]$SkipRun
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$gradlew = Join-Path $repo "gradlew.bat"

function Find-JarTool {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + $candidates + @("C:\Program Files\Java\jdk-26.0.1")
    foreach ($candidate in $candidates) {
        $jar = Join-Path $candidate "bin\jar.exe"
        if (Test-Path -LiteralPath $jar) { return $jar }
    }
    throw "No JDK jar tool found. Set JAVA_HOME."
}

try {
    if (-not (Test-Path -LiteralPath $gradlew)) { throw "Gradle wrapper missing: $gradlew" }

    Write-Host "TOOLCHAINFIXTURE STEP api-agent"
    & $gradlew "-p" $repo ":api:jar" ":core:agentJar"
    if ($LASTEXITCODE -ne 0) {
        throw "gradle :api:jar :core:agentJar failed with exit code $LASTEXITCODE"
    }

    Write-Host "TOOLCHAINFIXTURE STEP maven-publish"
    & $gradlew "-p" (Join-Path $root "maven-lib") "publish"
    if ($LASTEXITCODE -ne 0) {
        throw "gradle -p maven-lib publish failed with exit code $LASTEXITCODE"
    }

    $modsDir = Join-Path $root "build\weaver\mods"
    if (Test-Path -LiteralPath $modsDir) {
        Remove-Item -LiteralPath $modsDir -Recurse -Force
    }

    Write-Host "TOOLCHAINFIXTURE STEP mod-build"
    & $gradlew "-p" $root "weaverResolveMods" "weaverBuild" "weaverStage" "weaverExportJij"
    if ($LASTEXITCODE -ne 0) {
        throw "gradle weaverResolveMods weaverBuild weaverStage weaverExportJij failed with exit code $LASTEXITCODE"
    }

    $libsDir = Join-Path $root "build\libs"
    $mainJars = @(Get-ChildItem -LiteralPath $libsDir -Filter "*.jar" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" })
    if ($mainJars.Count -ne 1) {
        throw "expected exactly one fixture jar in $libsDir but found $($mainJars.Count)"
    }
    $mainJar = $mainJars[0].FullName

    $jarTool = Find-JarTool
    $entries = @(& $jarTool tf $mainJar)
    if ($LASTEXITCODE -ne 0) { throw "jar tf failed for $mainJar" }

    $required = @(
        "META-INF/weaver/jij.json",
        "META-INF/weaver/jars/fixture-lib-1.0.0.jar",
        "META-INF/weaver/jars/fixture-transitive-1.0.0.jar",
        "META-INF/weaver/jars/fixture-nested-1.0.0.jar"
    )
    $missing = @($required | Where-Object { $entries -notcontains $_ })
    if ($missing.Count -gt 0) {
        throw "fixture jar is missing entries: $($missing -join ', ')"
    }

    $optionalEntries = @($entries | Where-Object { $_ -match "fixture-optional" })
    if ($optionalEntries.Count -gt 0) {
        throw "fixture jar must not embed fixture-optional: $($optionalEntries -join ', ')"
    }

    $nestedCount = @($entries | Where-Object { $_ -like "META-INF/weaver/jars/*.jar" }).Count
    Write-Host "TOOLCHAINFIXTURE BUILD ok=true jar=$mainJar nested=$nestedCount optional=false"

    if (-not $SkipRun) {
        Write-Host "TOOLCHAINFIXTURE STEP loader-proof"
        & powershell "-NoProfile" "-ExecutionPolicy" "Bypass" "-File" (Join-Path $root "loader-proof.ps1") "-MainJar" $mainJar "-ExportDir" $modsDir
        if ($LASTEXITCODE -ne 0) {
            throw "loader proof failed with exit code $LASTEXITCODE"
        }
    }

    exit 0
} catch {
    Write-Host "TOOLCHAINFIXTURE BUILD ok=false reason=$($_.Exception.Message)"
    exit 1
}
