#requires -Version 5.1
[CmdletBinding()]
param()

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$probeSource = Join-Path $root 'tools\arabic-probe\EditBoxProbe.java'
$agent = Join-Path $root 'core\build\agent\weaver-agent.jar'
$gameDir = if ($env:WEAVER_GAME_DIR) {
    $env:WEAVER_GAME_DIR
} else {
    Join-Path $root 'work\game-26.3'
}
$classpathFile = Join-Path $gameDir 'classpath.txt'

if (-not (Test-Path -LiteralPath $classpathFile -PathType Leaf)) {
    Write-Host "WEAVER ARABIC PROBE ok=false reason=no-game-classpath"
    exit 1
}
if (-not (Test-Path -LiteralPath $agent -PathType Leaf)) {
    Write-Host "WEAVER ARABIC PROBE ok=false reason=agent-missing run=.\\gradlew.bat :core:agentJar"
    exit 1
}

$jdk = @(& (Join-Path $PSScriptRoot 'jdk.ps1'))[0]
if (-not $jdk) { $jdk = $env:JAVA_HOME }
if (-not $jdk -or -not (Test-Path -LiteralPath (Join-Path $jdk 'bin\javac.exe'))) {
    $jdk = 'C:\Program Files\Java\jdk-26.0.1'
}
$javac = Join-Path $jdk 'bin\javac.exe'
$java = Join-Path $jdk 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javac) -or -not (Test-Path -LiteralPath $java)) {
    Write-Host "WEAVER ARABIC PROBE ok=false reason=jdk26-missing"
    exit 1
}

$work = Join-Path ([System.IO.Path]::GetTempPath()) ('weaver-arabic-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $work -Force | Out-Null
$mods = Join-Path $work 'mods'
New-Item -ItemType Directory -Path $mods -Force | Out-Null

& $javac -d $work $probeSource
if ($LASTEXITCODE -ne 0) {
    Write-Host "WEAVER ARABIC PROBE ok=false reason=probe-compile"
    exit 1
}

$gameCp = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
$probeCp = "$gameCp;$work"
$agentArg = "-javaagent:$agent=side=client;modsDir=$mods;configDir=$work\config;dataDir=$work\data"
$output = & $java $agentArg -cp $probeCp EditBoxProbe 2>&1 | ForEach-Object { $_.ToString() }
$exit = $LASTEXITCODE
$text = $output -join "`n"

$applied = $text -match 'MIXIN APPLIED ok=true mod=weaver-client .*target=net\.minecraft\.client\.gui\.components\.EditBox'
$loaded = $text -match 'EDITBOX_LOADED=net\.minecraft\.client\.gui\.components\.EditBox'
if ($exit -eq 0 -and $applied -and $loaded) {
    Write-Host "WEAVER ARABIC PROBE ok=true"
    exit 0
}

Write-Host ("WEAVER ARABIC PROBE ok=false applied=" + $applied + " loaded=" + $loaded + " exit=" + $exit)
$output | Select-Object -Last 40 | ForEach-Object { Write-Host ("  | " + $_) }
exit 1
