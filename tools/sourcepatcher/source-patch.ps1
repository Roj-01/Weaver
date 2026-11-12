#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$ClientJar = "",
    [string]$ServerJar = "",
    [string]$OutDir = "",
    [string]$VineflowerJar = "",
    [string]$TargetsFile = "",
    [string]$PatchesDir = ""
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$repo = Split-Path -Parent (Split-Path -Parent $root)
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

function Convert-ToLf {
    param([Parameter(Mandatory = $true)][string]$Path)
    $text = [System.IO.File]::ReadAllText($Path)
    $normalized = $text.Replace("`r`n", "`n").Replace("`r", "`n")
    [System.IO.File]::WriteAllText($Path, $normalized,
        (New-Object System.Text.UTF8Encoding($false)))
}

function ConvertTo-JsonString {
    param([AllowEmptyString()][string]$Value)
    $builder = New-Object System.Text.StringBuilder
    [void]$builder.Append('"')
    foreach ($character in $Value.ToCharArray()) {
        $code = [int]$character
        if ($character -eq [char]'"') {
            [void]$builder.Append('\"')
        }
        elseif ($character -eq [char]'\') {
            [void]$builder.Append('\\')
        }
        elseif ($character -eq "`n") {
            [void]$builder.Append('\n')
        }
        elseif ($character -eq "`r") {
            [void]$builder.Append('\r')
        }
        elseif ($character -eq "`t") {
            [void]$builder.Append('\t')
        }
        elseif ($code -lt 32) {
            [void]$builder.Append(('\u{0:x4}' -f $code))
        }
        else {
            [void]$builder.Append($character)
        }
    }
    [void]$builder.Append('"')
    return $builder.ToString()
}

function Expand-TargetClasses {
    param(
        [Parameter(Mandatory = $true)][string]$Jar,
        [Parameter(Mandatory = $true)][string]$Destination,
        [Parameter(Mandatory = $true)][string[]]$Targets
    )
    $rootPath = [System.IO.Path]::GetFullPath($Destination).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar)
    $prefix = $rootPath + [System.IO.Path]::DirectorySeparatorChar
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        foreach ($target in $Targets) {
            $base = $target.Replace('.', '/')
            $exact = $base + ".class"
            $innerPrefix = $base + '$'
            $matches = @($zip.Entries | Where-Object {
                    $_.FullName -ceq $exact -or
                    ($_.FullName.StartsWith($innerPrefix, [StringComparison]::Ordinal) -and
                        $_.FullName.EndsWith(".class", [StringComparison]::Ordinal))
                })
            $mainEntries = @($matches | Where-Object { $_.FullName -ceq $exact })
            if ($mainEntries.Count -ne 1) {
                throw "target class entry missing or duplicated in $Jar`: $exact"
            }
            foreach ($entry in $matches) {
                $destinationPath = [System.IO.Path]::GetFullPath(
                    (Join-Path $Destination $entry.FullName))
                if (-not ($destinationPath -eq $rootPath -or
                        $destinationPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase))) {
                    throw "unsafe entry path in $Jar`: $($entry.FullName)"
                }
                $parent = [System.IO.Path]::GetDirectoryName($destinationPath)
                if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
                    New-Item -ItemType Directory -Path $parent -Force | Out-Null
                }
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile(
                    $entry, $destinationPath, $true)
            }
        }
    }
    finally {
        $zip.Dispose()
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

function New-MetadataJson {
    param([Parameter(Mandatory = $true)][string]$SourceSha256)
    $sortedTargets = @($targetNames | Sort-Object)
    $sortedPatches = @($patchNames | Sort-Object)
    $builder = New-Object System.Text.StringBuilder
    [void]$builder.Append("{`n")
    [void]$builder.Append('  "patchSet": ').Append((ConvertTo-JsonString $patchSet)).Append(",`n")
    [void]$builder.Append('  "decompiler": ').Append((ConvertTo-JsonString $decompilerName)).Append(",`n")
    [void]$builder.Append('  "decompilerVersion": ').Append((ConvertTo-JsonString $decompilerVersion)).Append(",`n")
    [void]$builder.Append('  "decompilerSha256": ').Append((ConvertTo-JsonString $decompilerSha256)).Append(",`n")
    [void]$builder.Append('  "javaRelease": ').Append($javaRelease).Append(",`n")
    [void]$builder.Append('  "sourceSha256": ').Append((ConvertTo-JsonString $SourceSha256)).Append(",`n")
    [void]$builder.Append('  "classes": [').Append("`n")
    for ($index = 0; $index -lt $sortedTargets.Count; $index++) {
        [void]$builder.Append('    ')
        [void]$builder.Append((ConvertTo-JsonString $sortedTargets[$index]))
        [void]$builder.Append($(if ($index + 1 -lt $sortedTargets.Count) { ",`n" } else { "`n" }))
    }
    [void]$builder.Append('  ],').Append("`n")
    [void]$builder.Append('  "patches": [').Append("`n")
    for ($index = 0; $index -lt $sortedPatches.Count; $index++) {
        [void]$builder.Append('    ')
        [void]$builder.Append((ConvertTo-JsonString $sortedPatches[$index]))
        [void]$builder.Append($(if ($index + 1 -lt $sortedPatches.Count) { ",`n" } else { "`n" }))
    }
    [void]$builder.Append('  ],').Append("`n")
    [void]$builder.Append('  "patchCount": ').Append($sortedPatches.Count).Append(",`n")
    [void]$builder.Append('  "targets": [').Append("`n")
    for ($index = 0; $index -lt $sortedTargets.Count; $index++) {
        [void]$builder.Append('    ')
        [void]$builder.Append((ConvertTo-JsonString $sortedTargets[$index]))
        [void]$builder.Append($(if ($index + 1 -lt $sortedTargets.Count) { ",`n" } else { "`n" }))
    }
    [void]$builder.Append('  ]').Append("`n")
    [void]$builder.Append('}').Append("`n")
    return $builder.ToString()
}

function New-ReportJson {
    param(
        [Parameter(Mandatory = $true)][string]$Side,
        [Parameter(Mandatory = $true)][string]$InputJar,
        [Parameter(Mandatory = $true)][string]$InputSha256,
        [Parameter(Mandatory = $true)][string]$OutputJar,
        [Parameter(Mandatory = $true)][string]$OutputSha256,
        [Parameter(Mandatory = $true)][int]$ClassCount,
        [Parameter(Mandatory = $true)][int]$VerifiedClassCount,
        [Parameter(Mandatory = $true)][int]$ElapsedMs
    )
    $sortedTargets = @($targetNames | Sort-Object)
    $sortedPatches = @($patchNames | Sort-Object)
    $inputPath = $InputJar.Replace('\', '/')
    $outputPath = $OutputJar.Replace('\', '/')
    $builder = New-Object System.Text.StringBuilder
    [void]$builder.Append("{`n")
    [void]$builder.Append('  "patchSet": ').Append((ConvertTo-JsonString $patchSet)).Append(",`n")
    [void]$builder.Append('  "side": ').Append((ConvertTo-JsonString $Side)).Append(",`n")
    [void]$builder.Append('  "input": ').Append((ConvertTo-JsonString $inputPath)).Append(",`n")
    [void]$builder.Append('  "inputSha256": ').Append((ConvertTo-JsonString $InputSha256)).Append(",`n")
    [void]$builder.Append('  "output": ').Append((ConvertTo-JsonString $outputPath)).Append(",`n")
    [void]$builder.Append('  "outputSha256": ').Append((ConvertTo-JsonString $OutputSha256)).Append(",`n")
    [void]$builder.Append('  "classCount": ').Append($ClassCount).Append(",`n")
    [void]$builder.Append('  "verifiedClassCount": ').Append($VerifiedClassCount).Append(",`n")
    [void]$builder.Append('  "targets": [').Append("`n")
    for ($index = 0; $index -lt $sortedTargets.Count; $index++) {
        [void]$builder.Append('    ')
        [void]$builder.Append((ConvertTo-JsonString $sortedTargets[$index]))
        [void]$builder.Append($(if ($index + 1 -lt $sortedTargets.Count) { ",`n" } else { "`n" }))
    }
    [void]$builder.Append('  ],').Append("`n")
    [void]$builder.Append('  "applied": [').Append("`n")
    for ($index = 0; $index -lt $sortedPatches.Count; $index++) {
        [void]$builder.Append('    ')
        [void]$builder.Append((ConvertTo-JsonString $sortedPatches[$index]))
        [void]$builder.Append($(if ($index + 1 -lt $sortedPatches.Count) { ",`n" } else { "`n" }))
    }
    [void]$builder.Append('  ],').Append("`n")
    [void]$builder.Append('  "elapsedMs": ').Append($ElapsedMs).Append("`n")
    [void]$builder.Append('}').Append("`n")
    return $builder.ToString()
}

function Invoke-SourceSide {
    param(
        [Parameter(Mandatory = $true)][string]$Side,
        [Parameter(Mandatory = $true)][string]$InputJar,
        [Parameter(Mandatory = $true)][string]$OutputJar,
        [Parameter(Mandatory = $true)][string]$ReportPath,
        [Parameter(Mandatory = $true)][string]$ClasspathText,
        [Parameter(Mandatory = $true)][string]$LibrariesFile
    )
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $work = Join-Path $workRoot $Side
    if (Test-Path -LiteralPath $work) {
        Remove-Item -LiteralPath $work -Recurse -Force
    }
    $classesDir = Join-Path $work "classes"
    $srcDir = Join-Path $work "src"
    $compiledDir = Join-Path $work "compiled"
    $patchWork = Join-Path $work "patches"
    foreach ($directory in @($work, $classesDir, $srcDir, $compiledDir, $patchWork)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }

    Expand-TargetClasses -Jar $InputJar -Destination $classesDir -Targets $targets

    $decompileArguments = @(
        "-jar", $vineflowerPath, "-dgs=1", "-e=$ClasspathText", $classesDir, $srcDir)
    $decompile = Invoke-Native -FilePath $java -Arguments $decompileArguments
    Write-Utf8NoBom -Path (Join-Path $work "vineflower.log") `
        -Text ((@($decompile.Output) + @("")) -join "`n")
    if ($decompile.ExitCode -ne 0) {
        throw "Vineflower failed for $Side`:`n$($decompile.Output -join "`n")"
    }

    foreach ($target in $targets) {
        $sourceFile = Join-Path $srcDir ($target.Replace('.', '\') + ".java")
        if (-not (Test-Path -LiteralPath $sourceFile -PathType Leaf)) {
            throw "Vineflower did not produce $sourceFile"
        }
    }

    $sourceFiles = @(Get-ChildItem -LiteralPath $srcDir -Recurse -Filter *.java -File |
        Sort-Object FullName |
        ForEach-Object { $_.FullName })
    if ($sourceFiles.Count -eq 0) {
        throw "Vineflower produced no Java sources for $Side"
    }
    foreach ($sourceFile in $sourceFiles) {
        Convert-ToLf -Path $sourceFile
    }

    foreach ($patchFile in $patchFiles) {
        $targetPatch = Join-Path $patchWork $patchFile.Name
        [System.IO.File]::Copy($patchFile.FullName, $targetPatch, $true)
        Convert-ToLf -Path $targetPatch
    }
    $appliedPatches = @(Get-ChildItem -LiteralPath $patchWork -Filter *.patch -File |
        Sort-Object Name)
    if ($appliedPatches.Count -ne $patchFiles.Count) {
        throw "source patch copy mismatch for $Side"
    }

    $git = Get-Command git.exe -ErrorAction SilentlyContinue
    if (-not $git) {
        throw "git is required to apply source patches"
    }
    $gitInit = Invoke-Native -FilePath $git.Source `
        -Arguments @("-C", $srcDir, "init", "--quiet")
    if ($gitInit.ExitCode -ne 0) {
        throw "git init failed for $Side`: $($gitInit.Output -join "`n")"
    }
    $gitConfig = Invoke-Native -FilePath $git.Source `
        -Arguments @("-C", $srcDir, "config", "core.autocrlf", "false")
    if ($gitConfig.ExitCode -ne 0) {
        throw "git config failed for $Side`: $($gitConfig.Output -join "`n")"
    }
    $gitEol = Invoke-Native -FilePath $git.Source `
        -Arguments @("-C", $srcDir, "config", "core.eol", "lf")
    if ($gitEol.ExitCode -ne 0) {
        throw "git config failed for $Side`: $($gitEol.Output -join "`n")"
    }
    $gitAdd = Invoke-Native -FilePath $git.Source `
        -Arguments @("-C", $srcDir, "add", "-A")
    if ($gitAdd.ExitCode -ne 0) {
        throw "git add failed for $Side`: $($gitAdd.Output -join "`n")"
    }

    foreach ($appliedPatch in $appliedPatches) {
        $gitApply = Invoke-Native -FilePath $git.Source -Arguments @(
            "-C", $srcDir,
            "-c", "core.autocrlf=false",
            "-c", "core.eol=lf",
            "apply", "--whitespace=nowarn", $appliedPatch.FullName)
        if ($gitApply.ExitCode -ne 0) {
            throw "source patch failed to apply for $Side`: $($appliedPatch.Name)`n$($gitApply.Output -join "`n")"
        }
        Write-Host "SOURCE PATCHER APPLY ok=true side=$Side patch=$($appliedPatch.Name)"
    }

    $javaSources = @(Get-ChildItem -LiteralPath $srcDir -Recurse -Filter *.java -File |
        Sort-Object FullName |
        ForEach-Object { $_.FullName })
    $compileArguments = @(
        "--release", "25", "-proc:none", "-encoding", "UTF-8",
        "-cp", $ClasspathText, "-d", $compiledDir) + $javaSources
    $compile = Invoke-Native -FilePath $javac -Arguments $compileArguments
    Write-Utf8NoBom -Path (Join-Path $work "javac.log") `
        -Text ((@($compile.Output) + @("")) -join "`n")
    if ($compile.ExitCode -ne 0) {
        throw "javac failed for $Side`:`n$($compile.Output -join "`n")"
    }

    foreach ($target in $targets) {
        $classFile = Join-Path $compiledDir ($target.Replace('.', '\') + ".class")
        if (-not (Test-Path -LiteralPath $classFile -PathType Leaf)) {
            throw "javac did not produce $classFile"
        }
    }
    $compiledClasses = @(Get-ChildItem -LiteralPath $compiledDir -Recurse -Filter *.class -File)
    if ($compiledClasses.Count -eq 0) {
        throw "javac produced no classes for $Side"
    }

    $sourceSha256 = Get-Sha256 -Path $InputJar
    $metadataPath = Join-Path $work "metadata.json"
    Write-Utf8NoBom -Path $metadataPath -Text (New-MetadataJson -SourceSha256 $sourceSha256)

    $replace = Invoke-Native -FilePath $java -Arguments @(
        "-cp", $toolClasspath,
        "net.weaver.sourcepatcher.ReplaceJar",
        "--input", $InputJar,
        "--output", $OutputJar,
        "--classes", $compiledDir,
        "--metadata", $metadataPath)
    Write-Utf8NoBom -Path (Join-Path $work "replace.log") `
        -Text ((@($replace.Output) + @("")) -join "`n")
    if ($replace.ExitCode -ne 0) {
        throw "ReplaceJar failed for $Side`:`n$($replace.Output -join "`n")"
    }

    $javapResult = Invoke-Native -FilePath $javap -Arguments @(
        "-p", "-s", "-cp", $OutputJar, "net.minecraft.world.item.ItemStack")
    Write-Utf8NoBom -Path (Join-Path $work "javap.log") `
        -Text ((@($javapResult.Output) + @("")) -join "`n")
    if ($javapResult.ExitCode -ne 0) {
        throw "javap failed for $Side`: $($javapResult.Output -join "`n")"
    }
    $javapLines = @($javapResult.Output | Where-Object { $null -ne $_ -and $_.Trim().Length -gt 0 })
    Assert-JavapMethod -Lines $javapLines -Name $probeMethod -Descriptor $probeDescriptor

    $verify = Invoke-Native -FilePath $java -Arguments @(
        "-Xverify:all", "-cp", $toolClasspath,
        "net.weaver.sourcepatcher.VerifyClasses", $OutputJar, $LibrariesFile)
    $verifyText = ($verify.Output -join "`n")
    Write-Host $verifyText
    Write-Utf8NoBom -Path (Join-Path $work "verify.log") `
        -Text (($verify.Output + @("")) -join "`n")
    if ($verify.ExitCode -ne 0) {
        throw "class verification failed for $Side`:`n$verifyText"
    }
    $marker = [regex]::Match($verifyText,
        'SOURCE PATCHER CLASSLOAD jar=[^\r\n]* ok=(\d+) failures=0')
    if (-not $marker.Success) {
        throw "class verification marker is missing or reports failures for $Side`:`n$verifyText"
    }
    $verifiedClasses = [int]$marker.Groups[1].Value
    Write-Host "SOURCE PATCHER CLASSLOAD ok=true side=$Side classes=$verifiedClasses failures=0"

    $outputSha256 = Get-Sha256 -Path $OutputJar
    $elapsedMs = [int]$stopwatch.ElapsedMilliseconds
    Write-Utf8NoBom -Path $ReportPath -Text (New-ReportJson `
        -Side $Side `
        -InputJar $InputJar `
        -InputSha256 $sourceSha256 `
        -OutputJar $OutputJar `
        -OutputSha256 $outputSha256 `
        -ClassCount $compiledClasses.Count `
        -VerifiedClassCount $verifiedClasses `
        -ElapsedMs $elapsedMs)

    Write-Host "SOURCE PATCHER PATCH ok=true side=$Side jar=$OutputJar classes=$($compiledClasses.Count) patches=$($patchNames.Count) verified=$verifiedClasses"
}

if (-not $ClientJar) { $ClientJar = Join-Path $repo "work\game-26.3\client.jar" }
if (-not $ServerJar) { $ServerJar = Join-Path $repo "work\server-26.3\versions\26.3\server-26.3.jar" }
if (-not $OutDir) { $OutDir = Join-Path $root "out" }
if (-not $TargetsFile) { $TargetsFile = Join-Path $root "targets.txt" }
if (-not $PatchesDir) { $PatchesDir = Join-Path $root "patches" }
if ($VineflowerJar) { $VineflowerJar = Get-FullPath $VineflowerJar }

$ClientJar = Get-FullPath $ClientJar
$ServerJar = Get-FullPath $ServerJar
$OutDir = Get-FullPath $OutDir
$TargetsFile = Get-FullPath $TargetsFile
$PatchesDir = Get-FullPath $PatchesDir

New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
$clientOutput = Join-Path $OutDir "client-source-patched.jar"
$serverOutput = Join-Path $OutDir "server-source-patched.jar"
$clientReport = Join-Path $OutDir "report-client.json"
$serverReport = Join-Path $OutDir "report-server.json"
$workRoot = Join-Path $OutDir "work"
foreach ($artifact in @($clientOutput, $serverOutput, $clientReport, $serverReport, $workRoot)) {
    if (Test-Path -LiteralPath $artifact) {
        Remove-Item -LiteralPath $artifact -Recurse -Force
    }
}
New-Item -ItemType Directory -Path $workRoot -Force | Out-Null

if (-not (Test-Path -LiteralPath $ClientJar -PathType Leaf)) {
    throw "client jar not found: $ClientJar"
}
if (-not (Test-Path -LiteralPath $ServerJar -PathType Leaf)) {
    throw "server jar not found: $ServerJar"
}
if (-not (Test-Path -LiteralPath $TargetsFile -PathType Leaf)) {
    throw "targets file not found: $TargetsFile"
}
if (-not (Test-Path -LiteralPath $PatchesDir -PathType Container)) {
    throw "patches directory not found: $PatchesDir"
}

$buildScript = Join-Path $root "build.ps1"
$buildParameters = @{}
if ($VineflowerJar) { $buildParameters["VineflowerJar"] = $VineflowerJar }
& $buildScript @buildParameters
if (-not $?) { throw "sourcepatcher build failed" }

$jdkPath = (Get-Content -LiteralPath (Join-Path $root "out\jdk-path.txt") -Raw).Trim()
$vineflowerPath = (Get-Content -LiteralPath (Join-Path $root "out\vineflower-path.txt") -Raw).Trim()
$toolClasspath = (Get-Content -LiteralPath (Join-Path $root "out\sourcepatcher-classpath.txt") -Raw).Trim()
$java = Join-Path $jdkPath "bin\java.exe"
$javac = Join-Path $jdkPath "bin\javac.exe"
$javap = Join-Path $jdkPath "bin\javap.exe"
foreach ($required in @($java, $javac, $javap, $vineflowerPath, $toolClasspath)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf) -and
        -not (Test-Path -LiteralPath $required -PathType Container)) {
        throw "sourcepatcher toolchain file missing: $required"
    }
}

$rawTargets = @(Get-Content -LiteralPath $TargetsFile |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_ -and -not $_.StartsWith("#") })
if ($rawTargets.Count -eq 0) {
    throw "targets file contains no class names: $TargetsFile"
}
$normalizedTargets = @()
foreach ($rawTarget in $rawTargets) {
    $candidate = $rawTarget.Replace('/', '.')
    if ($candidate -notmatch '^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$') {
        throw "invalid target class name: $rawTarget"
    }
    $normalizedTargets += $candidate
}
$targets = @($normalizedTargets | Sort-Object -Unique)
if ($targets.Count -ne $normalizedTargets.Count) {
    throw "targets file contains duplicate class names"
}
$targetNames = @($targets | ForEach-Object { $_.Replace('.', '/') } | Sort-Object)

$patchFiles = @(Get-ChildItem -LiteralPath $PatchesDir -Filter *.patch -File |
    Sort-Object Name)
if ($patchFiles.Count -eq 0) {
    throw "no source patches found in $PatchesDir"
}
$patchNames = @($patchFiles | ForEach-Object { $_.Name })
if (@($patchNames | Sort-Object -Unique).Count -ne $patchNames.Count) {
    throw "duplicate source patch names in $PatchesDir"
}

$clientClasspathFile = Join-Path $repo "work\game-26.3\classpath.txt"
if (-not (Test-Path -LiteralPath $clientClasspathFile -PathType Leaf)) {
    throw "client classpath file not found: $clientClasspathFile"
}
$clientClasspathText = (Get-Content -LiteralPath $clientClasspathFile -Raw).Trim()
if (-not $clientClasspathText) {
    throw "client classpath file is empty: $clientClasspathFile"
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
$serverClasspathText = (@($ServerJar) + $serverLibraries) -join ';'
$serverClasspathFile = Join-Path $workRoot "server-classpath.txt"
Write-Utf8NoBom -Path $serverClasspathFile -Text ($serverClasspathText + "`n")

try {
    Invoke-SourceSide -Side "client" `
        -InputJar $ClientJar `
        -OutputJar $clientOutput `
        -ReportPath $clientReport `
        -ClasspathText $clientClasspathText `
        -LibrariesFile $clientClasspathFile
    Invoke-SourceSide -Side "server" `
        -InputJar $ServerJar `
        -OutputJar $serverOutput `
        -ReportPath $serverReport `
        -ClasspathText $serverClasspathText `
        -LibrariesFile $serverClasspathFile
}
catch {
    foreach ($artifact in @($clientOutput, $serverOutput, $clientReport, $serverReport)) {
        if (Test-Path -LiteralPath $artifact) {
            Remove-Item -LiteralPath $artifact -Force -ErrorAction SilentlyContinue
        }
    }
    throw
}

Write-Host "SOURCE PATCHER GAME ok=true client=$clientOutput server=$serverOutput"
