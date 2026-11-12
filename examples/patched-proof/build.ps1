$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$gameDir = if ($env:WEAVER_GAME_DIR) { $env:WEAVER_GAME_DIR } else { Join-Path $repo "work\game-26.3" }
$agentLib = Join-Path $repo "core\build\agent\lib"
$agentJar = Join-Path $repo "core\build\agent\weaver-agent.jar"
$patcher = Join-Path $repo "tools\patcher\patch-game.ps1"

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + $candidates + @("C:\Program Files\Java\jdk-26.0.1")
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "bin\javac.exe")) { return $candidate }
    }
    throw "No JDK 26 found. Set JAVA_HOME."
}

function Assert-JarEntry {
    param(
        [string]$JarPath,
        [string]$Entry,
        [string]$Label
    )
    $entries = @(& $jarTool --list --file $JarPath)
    if ($LASTEXITCODE -ne 0) {
        throw "$Label cannot be read: $JarPath"
    }
    if ($entries -notcontains $Entry) {
        throw "$Label is missing required entry $Entry : $JarPath"
    }
}

$jdk = Find-Jdk
$javac = Join-Path $jdk "bin\javac.exe"
$jarTool = Join-Path $jdk "bin\jar.exe"

$classpathFile = Join-Path $gameDir "classpath.txt"
if (-not (Test-Path -LiteralPath $classpathFile -PathType Leaf)) {
    throw "game classpath missing: $classpathFile"
}
$clientJar = Join-Path $gameDir "client.jar"
if (-not (Test-Path -LiteralPath $clientJar -PathType Leaf)) {
    throw "original client jar missing: $clientJar"
}
if (-not (Test-Path -LiteralPath $agentLib -PathType Container)) {
    throw "agent lib missing: $agentLib (run .\gradlew.bat :core:agentJar)"
}
if (-not (Test-Path -LiteralPath $agentJar -PathType Leaf)) {
    throw "agent jar missing: $agentJar (run .\gradlew.bat :core:agentJar)"
}
if (-not (Test-Path -LiteralPath $patcher -PathType Leaf)) {
    throw "patcher tool missing: $patcher. The patcher workstream must deliver tools\patcher\patch-game.ps1; this proof does not fake patched jars."
}

$apiJar = Get-ChildItem -LiteralPath $agentLib -Filter "api-*.jar" -File |
    Where-Object { $_.Name -notlike "api-game-*" -and $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $apiJar) {
    throw "api jar missing: run .\gradlew.bat :api:jar"
}

$apiGameJar = Get-ChildItem -LiteralPath (Join-Path $repo "api-game\build\libs") -Filter "api-game-*.jar" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $apiGameJar) {
    $apiGameJar = Get-ChildItem -LiteralPath $agentLib -Filter "api-game-*.jar" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}
if (-not $apiGameJar) {
    throw "api-game jar missing: run .\gradlew.bat :api-game:jar"
}

$gameCp = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
$agentJars = @(Get-ChildItem -LiteralPath $agentLib -Filter *.jar -File | Sort-Object Name)
if ($agentJars.Count -eq 0) {
    throw "agent lib is empty: $agentLib (run .\gradlew.bat :core:agentJar)"
}
$libCp = ($agentJars | ForEach-Object { $_.FullName }) -join ";"

if (Test-Path -LiteralPath $out) {
    Remove-Item -LiteralPath $out -Recurse -Force
}
$classes = Join-Path $out "classes"
$mods = Join-Path $out "mods"
$patchedDir = Join-Path $out "patched"
$negativeClasses = Join-Path $out "negative-classes"
$negativeLog = Join-Path $out "negative-javac.log"
New-Item -ItemType Directory -Path $classes -Force | Out-Null
New-Item -ItemType Directory -Path $mods -Force | Out-Null
New-Item -ItemType Directory -Path $negativeClasses -Force | Out-Null

$patcherArguments = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass",
    "-File", $patcher,
    "-OutDir", $patchedDir
)
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    & powershell @patcherArguments
    $patcherExit = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorAction
}
if ($patcherExit -ne 0) {
    throw "patcher failed (exit $patcherExit): $patcher -OutDir $patchedDir"
}

$patchedClient = Join-Path $patchedDir "client-patched.jar"
$patchedServer = Join-Path $patchedDir "server-patched.jar"
if (-not (Test-Path -LiteralPath $patchedClient -PathType Leaf)) {
    throw "patcher did not produce client-patched.jar: $patchedClient"
}
if (-not (Test-Path -LiteralPath $patchedServer -PathType Leaf)) {
    throw "patcher did not produce server-patched.jar: $patchedServer"
}
Assert-JarEntry -JarPath $patchedClient -Entry "net/weaver/patched/WeaverAttachmentHolder.class" -Label "client-patched.jar"
Assert-JarEntry -JarPath $patchedClient -Entry "net/weaver/patched/PatchedGame.class" -Label "client-patched.jar"
Assert-JarEntry -JarPath $patchedServer -Entry "net/weaver/patched/WeaverAttachmentHolder.class" -Label "server-patched.jar"
Assert-JarEntry -JarPath $patchedServer -Entry "net/weaver/patched/PatchedGame.class" -Label "server-patched.jar"

$sources = @(Get-ChildItem -LiteralPath (Join-Path $root "src\main\java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName })
if ($sources.Count -eq 0) {
    throw "No Java sources"
}

$positiveCp = "$patchedClient;$gameCp;$libCp;$($apiGameJar.FullName)"
& $javac --release 26 -encoding UTF-8 -cp $positiveCp -d $classes $sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed against the patched client jar"
}

$negativeCp = "$gameCp;$libCp;$($apiGameJar.FullName)"
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    $negativeOutput = @(& $javac --release 26 -encoding UTF-8 -cp $negativeCp -d $negativeClasses $sources 2>&1)
    $negativeExit = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorAction
}
$negativeOutput | Set-Content -LiteralPath $negativeLog -Encoding UTF8
if ($negativeExit -eq 0) {
    Write-Host "PATCHED PROOF NEGATIVE ok=false"
    Write-Host "PATCHED PROOF NEGATIVE REASON reason=vanilla-compile-succeeded log=$negativeLog"
    throw "negative control failed: the same source compiled without the patched jar"
}
$negativeText = $negativeOutput -join "`n"
if ($negativeText -notmatch "net\.weaver\.patched") {
    Write-Host "PATCHED PROOF NEGATIVE ok=false"
    Write-Host "PATCHED PROOF NEGATIVE REASON reason=vanilla-failure-not-caused-by-patched-imports log=$negativeLog"
    throw "negative control failed: vanilla javac failed, but not because of the patched imports"
}
Write-Host "PATCHED PROOF NEGATIVE ok=true"
Write-Host "PATCHED PROOF NEGATIVE LOG log=$negativeLog"

Copy-Item -Path (Join-Path $root "src\main\resources\*") -Destination $classes -Recurse -Force
$modJar = Join-Path $out "patched-proof.jar"
& $jarTool --create --file $modJar -C $classes .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed"
}
Copy-Item -LiteralPath $modJar -Destination (Join-Path $mods "patched-proof.jar") -Force

Write-Host "PATCHED PROOF BUILD ok=true jar=$modJar mods=$mods"
