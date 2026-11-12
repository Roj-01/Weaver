#requires -Version 5.1
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$root = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$classes = Join-Path $out "classes"
$inject = Join-Path $out "inject"
$testClasses = Join-Path $out "test-classes"
$testWork = Join-Path $out "test-work"
$classpathFile = Join-Path $out "patcher-classpath.txt"

$jdkCandidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + @($env:JAVA_HOME, "C:\Program Files\Java\jdk-26.0.1") |
    Where-Object { $_ -and $_.Trim().Length -gt 0 }
$jdk = $null
foreach ($candidate in $jdkCandidates) {
    if (Test-Path -LiteralPath (Join-Path $candidate "bin\javac.exe") -PathType Leaf) {
        $jdk = $candidate
        break
    }
}
if (-not $jdk) {
    throw "No JDK found. Set JAVA_HOME or install C:\Program Files\Java\jdk-26.0.1."
}
$javac = Join-Path $jdk "bin\javac.exe"
$java = Join-Path $jdk "bin\java.exe"

$asmNames = @(
    "asm-9.11.1.jar",
    "asm-tree-9.11.1.jar",
    "asm-commons-9.11.1.jar",
    "asm-util-9.11.1.jar",
    "asm-analysis-9.11.1.jar"
)
$asmDir = Join-Path $repo "core\build\agent\lib"
$asm = @()
foreach ($name in $asmNames) {
    $jar = Join-Path $asmDir $name
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw "Missing ASM jar: $jar"
    }
    $asm += $jar
}

function Get-JavaSources {
    param([Parameter(Mandatory = $true)][string]$Directory)
    if (-not (Test-Path -LiteralPath $Directory -PathType Container)) {
        throw "Missing source directory: $Directory"
    }
    return @(Get-ChildItem -LiteralPath $Directory -Recurse -Filter *.java |
        Sort-Object FullName |
        ForEach-Object { $_.FullName })
}

$toolSources = Get-JavaSources (Join-Path $root "src")
$injectSources = Get-JavaSources (Join-Path $root "inject")
$testSources = Get-JavaSources (Join-Path $root "test")
if ($toolSources.Count -eq 0) { throw "No patcher sources found." }
if ($injectSources.Count -eq 0) { throw "No injected sources found." }
if ($testSources.Count -eq 0) { throw "No test sources found." }

foreach ($directory in @($classes, $inject, $testClasses, $testWork)) {
    if (Test-Path -LiteralPath $directory) {
        Remove-Item -LiteralPath $directory -Recurse -Force
    }
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
Remove-Item -LiteralPath $classpathFile -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $out -Force | Out-Null

& $javac --release 25 -encoding UTF-8 -d $inject @injectSources
if ($LASTEXITCODE -ne 0) { throw "javac failed for injected classes" }

$asmClasspath = $asm -join ";"
& $javac --release 25 -encoding UTF-8 -cp $asmClasspath -d $classes @toolSources
if ($LASTEXITCODE -ne 0) { throw "javac failed for patcher classes" }

$testClasspath = (@($classes) + $asm) -join ";"
& $javac --release 25 -encoding UTF-8 -cp $testClasspath -d $testClasses @testSources
if ($LASTEXITCODE -ne 0) { throw "javac failed for patcher tests" }

$toolClasspath = (@($classes) + $asm) -join ";"
[System.IO.File]::WriteAllText($classpathFile, $toolClasspath,
    (New-Object System.Text.UTF8Encoding($false)))

$testRuntimeClasspath = (@($classes) + $asm + @($inject) + @($testClasses)) -join ";"
& $java -cp $testRuntimeClasspath net.weaver.patcher.test.PatcherSelfTest $inject $testWork
if ($LASTEXITCODE -ne 0) { throw "patcher self-test failed" }

Write-Host "PATCHER BUILD ok=true classes=$classes inject=$inject classpath=$classpathFile"
