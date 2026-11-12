#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$ClientJar = "",
    [string]$ServerJar = ""
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Assert-UnsignedJar {
    param([Parameter(Mandatory = $true)][string]$Jar)
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        foreach ($entry in $zip.Entries) {
            $name = $entry.FullName.ToUpperInvariant()
            if ($name -match '^META-INF/[^/]+\.(SF|RSA|DSA|EC)$' -or
                $name -match '^META-INF/SIG-') {
                throw "patched jar still contains signature entry '$($entry.FullName)': $Jar"
            }
        }
        $manifest = $zip.GetEntry("META-INF/MANIFEST.MF")
        if ($null -ne $manifest) {
            $reader = New-Object System.IO.StreamReader($manifest.Open())
            try {
                $text = $reader.ReadToEnd()
            }
            finally {
                $reader.Dispose()
            }
            if ($text -match '(?im)^[A-Za-z0-9-]+-Digest:') {
                throw "patched jar manifest still contains digest attributes: $Jar"
            }
            if ($text -match '(?im)^(Signature-Version|Magic|Digest-Algorithms):') {
                throw "patched jar manifest still contains signature metadata: $Jar"
            }
        }
    }
    finally {
        $zip.Dispose()
    }
}

$root = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$repo = Split-Path -Parent (Split-Path -Parent $root)
$buildScript = Join-Path $root "build.ps1"
$patchScript = Join-Path $root "patch-game.ps1"
$out = Join-Path $root "out"
$verifyRoot = Join-Path $out "verify"

function Get-FullPath {
    param([Parameter(Mandatory = $true)][string]$Path)
    return $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Path)
}

if (-not $ClientJar) { $ClientJar = Join-Path $repo "work\game-26.3\client.jar" }
if (-not $ServerJar) { $ServerJar = Join-Path $repo "work\server-26.3\versions\26.3\server-26.3.jar" }
$ClientJar = Get-FullPath $ClientJar
$ServerJar = Get-FullPath $ServerJar

if (-not (Test-Path -LiteralPath $ClientJar -PathType Leaf)) { throw "client jar not found: $ClientJar" }
if (-not (Test-Path -LiteralPath $ServerJar -PathType Leaf)) { throw "server jar not found: $ServerJar" }

$jdkCandidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + @($env:JAVA_HOME, "C:\Program Files\Java\jdk-26.0.1") |
    Where-Object { $_ -and $_.Trim().Length -gt 0 }
$jdk = $null
foreach ($candidate in $jdkCandidates) {
    if ((Test-Path -LiteralPath (Join-Path $candidate "bin\java.exe") -PathType Leaf) -and
        (Test-Path -LiteralPath (Join-Path $candidate "bin\javap.exe") -PathType Leaf)) {
        $jdk = $candidate
        break
    }
}
if (-not $jdk) {
    throw "No JDK found. Set JAVA_HOME or install C:\Program Files\Java\jdk-26.0.1."
}
$java = Join-Path $jdk "bin\java.exe"
$javap = Join-Path $jdk "bin\javap.exe"

if (Test-Path -LiteralPath $verifyRoot) {
    Remove-Item -LiteralPath $verifyRoot -Recurse -Force
}
$run1 = Join-Path $verifyRoot "run1"
$run2 = Join-Path $verifyRoot "run2"
New-Item -ItemType Directory -Path $run1, $run2 -Force | Out-Null

& $buildScript
if (-not $?) { throw "patcher build/self-test failed" }

& $patchScript -ClientJar $ClientJar -ServerJar $ServerJar -OutDir $run1
if (-not $?) { throw "first patch run failed" }
& $patchScript -ClientJar $ClientJar -ServerJar $ServerJar -OutDir $run2
if (-not $?) { throw "second patch run failed" }

$client1 = Join-Path $run1 "client-patched.jar"
$server1 = Join-Path $run1 "server-patched.jar"
$client2 = Join-Path $run2 "client-patched.jar"
$server2 = Join-Path $run2 "server-patched.jar"

$clientHash1 = (Get-FileHash -LiteralPath $client1 -Algorithm SHA256).Hash.ToLowerInvariant()
$serverHash1 = (Get-FileHash -LiteralPath $server1 -Algorithm SHA256).Hash.ToLowerInvariant()
$clientHash2 = (Get-FileHash -LiteralPath $client2 -Algorithm SHA256).Hash.ToLowerInvariant()
$serverHash2 = (Get-FileHash -LiteralPath $server2 -Algorithm SHA256).Hash.ToLowerInvariant()

if ($clientHash1 -ne $clientHash2) {
    throw "client patched jar is not deterministic: $clientHash1 vs $clientHash2"
}
if ($serverHash1 -ne $serverHash2) {
    throw "server patched jar is not deterministic: $serverHash1 vs $serverHash2"
}

Assert-UnsignedJar -Jar $client1
Assert-UnsignedJar -Jar $server1
Write-Host "PATCHER VERIFY signatures=stripped"

function Assert-Report {
    param(
        [Parameter(Mandatory = $true)][string]$ReportPath,
        [Parameter(Mandatory = $true)][string]$JarPath,
        [Parameter(Mandatory = $true)][string]$InputJar,
        [Parameter(Mandatory = $true)][int]$Operations
    )
    if (-not (Test-Path -LiteralPath $ReportPath -PathType Leaf)) {
        throw "report missing: $ReportPath"
    }
    $report = Get-Content -LiteralPath $ReportPath -Raw | ConvertFrom-Json
    if ($report.patchSet -ne "weaver-26.3-v1") {
        throw "report patch set mismatch: $($report.patchSet)"
    }
    if ([int]$report.patchLevel -ne 1) {
        throw "report patch level mismatch: $($report.patchLevel)"
    }
    if ([int]$report.operationCount -ne $Operations) {
        throw "report operation count mismatch: $($report.operationCount)"
    }
    if (@($report.applied).Count -ne $Operations) {
        throw "report applied list mismatch: $(@($report.applied).Count)"
    }
    if (@($report.skipped).Count -ne 0) {
        throw "report skipped list is not empty"
    }
    $outputHash = (Get-FileHash -LiteralPath $JarPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($report.outputSha256 -ne $outputHash) {
        throw "report output hash mismatch: $ReportPath"
    }
    $inputHash = (Get-FileHash -LiteralPath $InputJar -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($report.inputSha256 -ne $inputHash) {
        throw "report input hash mismatch: $ReportPath"
    }
}

Assert-Report -ReportPath (Join-Path $run1 "report-client.json") -JarPath $client1 `
    -InputJar $ClientJar -Operations 42
Assert-Report -ReportPath (Join-Path $run1 "report-server.json") -JarPath $server1 `
    -InputJar $ServerJar -Operations 42

$holderMethods = @(
    @{ Name = "weaver`$getAttachment"; Desc = "(Ljava/lang/String;)Ljava/lang/Object;" },
    @{ Name = "weaver`$setAttachment"; Desc = "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;" },
    @{ Name = "weaver`$removeAttachment"; Desc = "(Ljava/lang/String;)Ljava/lang/Object;" },
    @{ Name = "weaver`$hasAttachment"; Desc = "(Ljava/lang/String;)Z" },
    @{ Name = "weaver`$patchLevel"; Desc = "()I" }
)

function Assert-JavapMethod {
    param(
        [Parameter(Mandatory = $true)][string[]]$Lines,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Descriptor
    )
    $needle = [regex]::Escape($Name) + "\("
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i] -match $needle) {
            $last = [Math]::Min($i + 4, $Lines.Count)
            for ($j = $i + 1; $j -lt $last; $j++) {
                if ($Lines[$j].Trim() -eq ("descriptor: " + $Descriptor)) {
                    return
                }
            }
        }
    }
    throw "javap did not report method $Name$Descriptor"
}

function Assert-JavapClass {
    param(
        [Parameter(Mandatory = $true)][string]$Jar,
        [Parameter(Mandatory = $true)][string]$Class,
        [Parameter(Mandatory = $true)][string[]]$Interfaces,
        [Parameter(Mandatory = $true)][string[]]$MethodNames
    )
    $lines = @(& $javap -p -s -cp $Jar $Class 2>&1 |
        Where-Object { $null -ne $_ -and $_.Trim().Length -gt 0 })
    if ($LASTEXITCODE -ne 0) {
        throw "javap failed for $Class in $Jar"
    }
    $text = $lines -join "`n"
    foreach ($interfaceName in $Interfaces) {
        if ($text -notmatch [regex]::Escape($interfaceName)) {
            throw "javap output for $Class is missing interface $interfaceName"
        }
    }
    foreach ($methodName in $MethodNames) {
        foreach ($spec in $holderMethods) {
            if ($spec.Name -eq $methodName) {
                Assert-JavapMethod -Lines $lines -Name $spec.Name -Descriptor $spec.Desc
            }
        }
    }
}

$holder = "net.weaver.patched.WeaverAttachmentHolder"
$patchedGame = "net.weaver.patched.PatchedGame"

foreach ($jar in @($client1, $server1)) {
    Assert-JavapClass -Jar $jar -Class "net.minecraft.world.item.ItemStack" `
        -Interfaces @($holder, $patchedGame) `
        -MethodNames @("weaver`$getAttachment", "weaver`$setAttachment",
            "weaver`$removeAttachment", "weaver`$hasAttachment", "weaver`$patchLevel")
    Assert-JavapClass -Jar $jar -Class "net.minecraft.world.level.Level" `
        -Interfaces @($holder, $patchedGame) `
        -MethodNames @("weaver`$getAttachment", "weaver`$setAttachment",
            "weaver`$removeAttachment", "weaver`$hasAttachment", "weaver`$patchLevel")
    foreach ($className in @("net.minecraft.world.entity.Entity",
            "net.minecraft.world.level.block.entity.BlockEntity",
            "net.minecraft.world.level.chunk.LevelChunk")) {
        Assert-JavapClass -Jar $jar -Class $className `
            -Interfaces @($holder) `
            -MethodNames @("weaver`$getAttachment", "weaver`$setAttachment",
                "weaver`$removeAttachment", "weaver`$hasAttachment", "weaver`$patchLevel")
    }
}

$classpathFile = Join-Path $repo "work\game-26.3\classpath.txt"
$patcherClasspath = (Get-Content -LiteralPath (Join-Path $out "patcher-classpath.txt") -Raw).Trim()
$testRuntimeClasspath = @(
    $patcherClasspath,
    (Join-Path $out "inject"),
    (Join-Path $out "test-classes")
) -join ";"

foreach ($jar in @($client1, $server1)) {
    & $java -cp $testRuntimeClasspath net.weaver.patcher.test.VerifyPatchedJar $jar $classpathFile
    if ($LASTEXITCODE -ne 0) {
        throw "classloader probe failed for $jar"
    }
}

$clientClasspathFile = Join-Path $repo "work\game-26.3\classpath.txt"
& $java -Xverify:all -cp $testRuntimeClasspath net.weaver.patcher.test.VerifyAllClasses `
    $client1 $clientClasspathFile 1000
if ($LASTEXITCODE -ne 0) {
    throw "client class-load verification failed"
}

$serverLibraries = Join-Path $repo "work\server-26.3\libraries"
if (Test-Path -LiteralPath $serverLibraries -PathType Container) {
    $serverClasspathFile = Join-Path $verifyRoot "server-libraries.txt"
    $serverLibrariesText = (Get-ChildItem -LiteralPath $serverLibraries -Recurse -Filter *.jar |
        ForEach-Object { $_.FullName }) -join ';'
    Set-Content -LiteralPath $serverClasspathFile -Value $serverLibrariesText -Encoding ASCII
    & $java -Xverify:all -cp $testRuntimeClasspath net.weaver.patcher.test.VerifyAllClasses `
        $server1 $serverClasspathFile 1000
    if ($LASTEXITCODE -ne 0) {
        throw "server class-load verification failed"
    }
}
else {
    Write-Host "PATCHER VERIFY server-classload=skip reason=missing-server-libraries"
}

Write-Host "PATCHER VERIFY determinism=ok"
Write-Host "PATCHER VERIFY client-sha256=$clientHash1"
Write-Host "PATCHER VERIFY server-sha256=$serverHash1"

$sourceVerify = Join-Path $repo 'tools\sourcepatcher\verify.ps1'
if (Test-Path -LiteralPath $sourceVerify -PathType Leaf) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $sourceVerify
    if ($LASTEXITCODE -ne 0) {
        throw "source patcher verification failed"
    }
    Write-Host "PATCHER VERIFY source=ok"
}
else {
    Write-Host "PATCHER VERIFY source=skip reason=missing-sourcepatcher"
}

Write-Host "PATCHER VERIFY ok=true"
