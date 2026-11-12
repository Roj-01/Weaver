#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$ClientJar = "",
    [string]$ServerJar = ""
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$repo = Split-Path -Parent (Split-Path -Parent $root)
$out = Join-Path $root "out"
$verifyRoot = Join-Path $out "verify"
$patchSet = "weaver-26.3-source-v1"
$decompilerName = "vineflower"
$decompilerVersion = "1.11.1"
$decompilerSha256 = "a615d07ddbbcd489369674f40e42df639c32be95410890b38f173d5c1e2ea39c"
$javaRelease = 25
$probeMethod = 'weaver$sourcePatchProbe'
$probeDescriptor = '()Ljava/lang/String;'

function Get-FullPath {
    param([Parameter(Mandatory = $true)][string]$Path)
    return $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Path)
}

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $lines = @(& $FilePath @Arguments 2>&1 | ForEach-Object { $_.ToString() })
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previous
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Output = $lines }
}

function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Text
    )
    [System.IO.File]::WriteAllText($Path, $Text,
        (New-Object System.Text.UTF8Encoding($false)))
}

function Get-Sha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-UnsignedJar {
    param(
        [Parameter(Mandatory = $true)][string]$Jar,
        [string]$ExpectedSourceSha256 = ""
    )
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -match '^META-INF/[^/]+\.(SF|RSA|DSA|EC)$' -or
                $entry.FullName -match '^META-INF/SIG-') {
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
        $metadata = $zip.GetEntry("META-INF/weaver/source-patch.json")
        if ($null -eq $metadata) {
            throw "patched jar is missing META-INF/weaver/source-patch.json: $Jar"
        }
        $reader = New-Object System.IO.StreamReader($metadata.Open())
        try {
            $metadataText = $reader.ReadToEnd()
        }
        finally {
            $reader.Dispose()
        }
        $metadataObject = $metadataText | ConvertFrom-Json
        if ($metadataObject.patchSet -ne $patchSet) {
            throw "patched jar metadata patchSet mismatch: $Jar"
        }
        if ($metadataObject.sourceSha256 -notmatch '^[0-9a-f]{64}$') {
            throw "patched jar metadata sourceSha256 is invalid: $Jar"
        }
        if ($ExpectedSourceSha256 -and
            $metadataObject.sourceSha256 -ne $ExpectedSourceSha256.ToLowerInvariant()) {
            throw "patched jar metadata sourceSha256 does not match the input jar: $Jar"
        }
        if ($metadataObject.decompiler -ne $decompilerName) {
            throw "patched jar metadata decompiler mismatch: $Jar"
        }
        if ($metadataObject.decompilerVersion -ne $decompilerVersion) {
            throw "patched jar metadata decompilerVersion mismatch: $Jar"
        }
        if ($metadataObject.decompilerSha256 -ne $decompilerSha256) {
            throw "patched jar metadata decompilerSha256 mismatch: $Jar"
        }
        if ([int]$metadataObject.javaRelease -ne $javaRelease) {
            throw "patched jar metadata javaRelease mismatch: $Jar"
        }
        $classes = @($metadataObject.classes)
        $patches = @($metadataObject.patches)
        $targets = @($metadataObject.targets)
        if ($classes.Count -eq 0) {
            throw "patched jar metadata classes list is empty: $Jar"
        }
        if ($patches.Count -eq 0) {
            throw "patched jar metadata patches list is empty: $Jar"
        }
        if ($targets.Count -eq 0) {
            throw "patched jar metadata targets list is empty: $Jar"
        }
        if ([int]$metadataObject.patchCount -ne $patches.Count) {
            throw "patched jar metadata patchCount mismatch: $Jar"
        }
        if ($classes -notcontains "net/minecraft/world/item/ItemStack") {
            throw "patched jar metadata classes list is missing ItemStack: $Jar"
        }
        $classNamePattern = '^[A-Za-z_$][A-Za-z0-9_$]*([./][A-Za-z_$][A-Za-z0-9_$]*)*$'
        foreach ($className in $classes) {
            if ($className -notmatch $classNamePattern -or
                $className.Contains('\') -or $className.Contains(':')) {
                throw "patched jar metadata contains an invalid class name: $className"
            }
        }
        foreach ($patchName in $patches) {
            if ($patchName -match '[/\\:]') {
                throw "patched jar metadata contains a path-like patch name: $patchName"
            }
        }
        foreach ($targetName in $targets) {
            if ($targetName -notmatch $classNamePattern -or
                $targetName.Contains('\') -or $targetName.Contains(':')) {
                throw "patched jar metadata contains an invalid target name: $targetName"
            }
        }
        Assert-SortedOrdinal -Values $classes
        Assert-SortedOrdinal -Values $patches
        Assert-SortedOrdinal -Values $targets
        if ($metadataText -match '(?i)timestamp|elapsed|username') {
            throw "patched jar metadata contains forbidden volatile fields: $Jar"
        }
        if ($metadataText.Contains('\') -or $metadataText -match '(?i)[A-Za-z]:[\\/]') {
            throw "patched jar metadata contains an absolute filesystem path: $Jar"
        }
    }
    finally {
        $zip.Dispose()
    }
}

function Assert-SortedOrdinal {
    param([Parameter(Mandatory = $true)][string[]]$Values)
    for ($index = 1; $index -lt $Values.Count; $index++) {
        if ([StringComparer]::Ordinal.Compare($Values[$index - 1], $Values[$index]) -ge 0) {
            throw "metadata values are not sorted and unique: $($Values -join ', ')"
        }
    }
}

function Assert-JavapMethod {
    param(
        [Parameter(Mandatory = $true)][string[]]$Lines,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Descriptor
    )
    $needle = [regex]::Escape($Name) + "\("
    for ($index = 0; $index -lt $Lines.Count; $index++) {
        if ($Lines[$index] -match $needle) {
            if ($Lines[$index] -notmatch 'public') {
                throw "javap reported $Name but it is not public"
            }
            $last = [Math]::Min($index + 5, $Lines.Count)
            for ($cursor = $index + 1; $cursor -lt $last; $cursor++) {
                if ($Lines[$cursor].Trim() -eq ("descriptor: " + $Descriptor)) {
                    return
                }
            }
        }
    }
    throw "javap did not report method $Name$Descriptor"
}

function Assert-ClassLoad {
    param(
        [Parameter(Mandatory = $true)][string]$Jar,
        [Parameter(Mandatory = $true)][string]$LibrariesFile,
        [Parameter(Mandatory = $true)][string]$Java,
        [Parameter(Mandatory = $true)][string]$ToolClasspath
    )
    $result = Invoke-Native -FilePath $Java -Arguments @(
        "-Xverify:all", "-cp", $ToolClasspath,
        "net.weaver.sourcepatcher.VerifyClasses", $Jar, $LibrariesFile)
    $text = ($result.Output -join "`n")
    Write-Host $text
    if ($result.ExitCode -ne 0) {
        throw "class verification failed for $Jar`:`n$text"
    }
    $marker = [regex]::Match($text,
        'SOURCE PATCHER CLASSLOAD jar=[^\r\n]* ok=(\d+) failures=0')
    if (-not $marker.Success) {
        throw "classload marker is missing or reports failures for $Jar`:`n$text"
    }
    return [int]$marker.Groups[1].Value
}

if (-not $ClientJar) { $ClientJar = Join-Path $repo "work\game-26.3\client.jar" }
if (-not $ServerJar) { $ServerJar = Join-Path $repo "work\server-26.3\versions\26.3\server-26.3.jar" }
$ClientJar = Get-FullPath $ClientJar
$ServerJar = Get-FullPath $ServerJar

if (-not (Test-Path -LiteralPath $ClientJar -PathType Leaf)) {
    throw "client jar not found: $ClientJar"
}
if (-not (Test-Path -LiteralPath $ServerJar -PathType Leaf)) {
    throw "server jar not found: $ServerJar"
}

$buildScript = Join-Path $root "build.ps1"
$patchScript = Join-Path $root "source-patch.ps1"
if (Test-Path -LiteralPath $verifyRoot) {
    Remove-Item -LiteralPath $verifyRoot -Recurse -Force
}
& $buildScript
if (-not $?) { throw "sourcepatcher build failed" }

$jdkPath = (Get-Content -LiteralPath (Join-Path $out "jdk-path.txt") -Raw).Trim()
$toolClasspath = (Get-Content -LiteralPath (Join-Path $out "sourcepatcher-classpath.txt") -Raw).Trim()
$java = Join-Path $jdkPath "bin\java.exe"
$javap = Join-Path $jdkPath "bin\javap.exe"

$run1 = Join-Path $verifyRoot "run1"
$run2 = Join-Path $verifyRoot "run2"
New-Item -ItemType Directory -Path $run1, $run2 -Force | Out-Null

& $patchScript -ClientJar $ClientJar -ServerJar $ServerJar -OutDir $run1
if (-not $?) { throw "first source patch run failed" }
& $patchScript -ClientJar $ClientJar -ServerJar $ServerJar -OutDir $run2
if (-not $?) { throw "second source patch run failed" }

$client1 = Join-Path $run1 "client-source-patched.jar"
$server1 = Join-Path $run1 "server-source-patched.jar"
$client2 = Join-Path $run2 "client-source-patched.jar"
$server2 = Join-Path $run2 "server-source-patched.jar"

$clientHash1 = Get-Sha256 -Path $client1
$serverHash1 = Get-Sha256 -Path $server1
$clientHash2 = Get-Sha256 -Path $client2
$serverHash2 = Get-Sha256 -Path $server2

if ($clientHash1 -ne $clientHash2) {
    throw "client source-patched jar is not deterministic: $clientHash1 vs $clientHash2"
}
if ($serverHash1 -ne $serverHash2) {
    throw "server source-patched jar is not deterministic: $serverHash1 vs $serverHash2"
}
Write-Host "SOURCE PATCHER VERIFY determinism=ok"

$clientInputHash = Get-Sha256 -Path $ClientJar
$serverInputHash = Get-Sha256 -Path $ServerJar
Assert-UnsignedJar -Jar $client1 -ExpectedSourceSha256 $clientInputHash
Assert-UnsignedJar -Jar $server1 -ExpectedSourceSha256 $serverInputHash
Write-Host "SOURCE PATCHER VERIFY signatures=stripped"

foreach ($jar in @($client1, $server1)) {
    $javapResult = Invoke-Native -FilePath $javap -Arguments @(
        "-p", "-s", "-cp", $jar, "net.minecraft.world.item.ItemStack")
    if ($javapResult.ExitCode -ne 0) {
        throw "javap failed for $jar`: $($javapResult.Output -join "`n")"
    }
    $javapLines = @($javapResult.Output | Where-Object { $null -ne $_ -and $_.Trim().Length -gt 0 })
    Assert-JavapMethod -Lines $javapLines `
        -Name $probeMethod -Descriptor $probeDescriptor
}
Write-Host "SOURCE PATCHER VERIFY probe-method=present"

$clientLibrariesFile = Join-Path $repo "work\game-26.3\classpath.txt"
if (-not (Test-Path -LiteralPath $clientLibrariesFile -PathType Leaf)) {
    throw "client classpath file not found: $clientLibrariesFile"
}
$serverLibrariesDirectory = Join-Path $repo "work\server-26.3\libraries"
if (-not (Test-Path -LiteralPath $serverLibrariesDirectory -PathType Container)) {
    throw "server libraries directory not found: $serverLibrariesDirectory"
}
$serverLibraries = @(Get-ChildItem -LiteralPath $serverLibrariesDirectory -Recurse -Filter *.jar -File |
    Sort-Object FullName |
    ForEach-Object { $_.FullName })
if ($serverLibraries.Count -eq 0) {
    throw "no server libraries found in $serverLibrariesDirectory"
}
$serverLibrariesFile = Join-Path $verifyRoot "server-libraries.txt"
Write-Utf8NoBom -Path $serverLibrariesFile -Text (($serverLibraries -join ';') + "`n")

$clientVerified = Assert-ClassLoad -Jar $client1 -LibrariesFile $clientLibrariesFile `
    -Java $java -ToolClasspath $toolClasspath
$serverVerified = Assert-ClassLoad -Jar $server1 -LibrariesFile $serverLibrariesFile `
    -Java $java -ToolClasspath $toolClasspath
Write-Host "SOURCE PATCHER CLASSLOAD ok=true side=client classes=$clientVerified failures=0"
Write-Host "SOURCE PATCHER CLASSLOAD ok=true side=server classes=$serverVerified failures=0"

Write-Host "SOURCE PATCHER VERIFY client-sha256=$clientHash1"
Write-Host "SOURCE PATCHER VERIFY server-sha256=$serverHash1"
Write-Host "SOURCE PATCHER VERIFY ok=true determinism=ok signatures=stripped member=ok client-sha256=$clientHash1 server-sha256=$serverHash1 client-classes=$clientVerified server-classes=$serverVerified"
