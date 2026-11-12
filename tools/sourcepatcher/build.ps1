#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$VineflowerJar = ""
)

$ErrorActionPreference = "Stop"

$root = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$out = Join-Path $root "out"
$classes = Join-Path $out "classes"
$vineflowerDirectory = Join-Path $out "vineflower"
$vineflowerPin = "vineflower-1.11.1.jar"
$vineflowerUrl = "https://repo1.maven.org/maven2/org/vineflower/vineflower/1.11.1/vineflower-1.11.1.jar"
$vineflowerSha256 = "a615d07ddbbcd489369674f40e42df639c32be95410890b38f173d5c1e2ea39c"

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

function Get-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME -and $env:JAVA_HOME.Trim().Length -gt 0) {
        $candidates += $env:JAVA_HOME.Trim()
    }
    $candidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + $candidates + @("C:\Program Files\Java\jdk-26.0.1")
    $command = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($command -and $command.Source) {
        $candidates += (Split-Path -Parent (Split-Path -Parent $command.Source))
    }

    $seen = @{}
    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        try {
            $full = [System.IO.Path]::GetFullPath($candidate)
        }
        catch {
            continue
        }
        $key = $full.ToLowerInvariant()
        if ($seen.ContainsKey($key)) { continue }
        $seen[$key] = $true

        $javac = Join-Path $full "bin\javac.exe"
        $java = Join-Path $full "bin\java.exe"
        $javap = Join-Path $full "bin\javap.exe"
        if (-not (Test-Path -LiteralPath $javac -PathType Leaf)) { continue }
        if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { continue }
        if (-not (Test-Path -LiteralPath $javap -PathType Leaf)) { continue }

        $version = Invoke-Native -FilePath $javac -Arguments @("-version")
        if ($version.ExitCode -ne 0) { continue }
        $text = ($version.Output -join " ").Trim()
        if ($text -notmatch 'javac\s+(\d+)') { continue }
        $major = [int]$Matches[1]
        if ($major -lt 25) { continue }
        return [pscustomobject]@{
            Path = $full
            Javac = $javac
            Java = $java
            Javap = $javap
            Version = $text
        }
    }
    throw "No JDK 25+ found. Set JAVA_HOME or install C:\Program Files\Java\jdk-26.0.1."
}

function Resolve-Vineflower {
    param([string]$Explicit)

    if ($Explicit) {
        $explicitPath = [System.IO.Path]::GetFullPath($Explicit)
        if (-not (Test-Path -LiteralPath $explicitPath -PathType Leaf)) {
            throw "Vineflower jar not found: $explicitPath"
        }
        $hash = Get-Sha256 -Path $explicitPath
        if ($hash -ne $vineflowerSha256) {
            throw "Vineflower SHA-256 mismatch for $explicitPath`: expected $vineflowerSha256, got $hash"
        }
        return $explicitPath
    }

    foreach ($candidate in @(
            (Join-Path $vineflowerDirectory $vineflowerPin),
            (Join-Path $vineflowerDirectory "vineflower.jar"))) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            $hash = Get-Sha256 -Path $candidate
            if ($hash -eq $vineflowerSha256) {
                return $candidate
            }
            Write-Warning "Ignoring cached Vineflower with unexpected SHA-256: $candidate"
        }
    }

    New-Item -ItemType Directory -Path $vineflowerDirectory -Force | Out-Null
    $target = Join-Path $vineflowerDirectory $vineflowerPin
    $temporary = Join-Path $vineflowerDirectory (".vineflower-" + [Guid]::NewGuid().ToString("N") + ".tmp")
    try {
        $downloaded = $false
        try {
            $previousProgress = $ProgressPreference
            $ProgressPreference = "SilentlyContinue"
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -Uri $vineflowerUrl -OutFile $temporary -UseBasicParsing
            $ProgressPreference = $previousProgress
            $downloaded = $true
        }
        catch {
            Write-Warning "Invoke-WebRequest failed: $($_.Exception.Message)"
        }
        if (-not $downloaded) {
            $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
            if (-not $curl) {
                throw "Unable to download Vineflower and curl.exe is unavailable."
            }
            Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
            $result = Invoke-Native -FilePath $curl.Source -Arguments @(
                "-L", "--fail", "--silent", "--show-error",
                "--output", $temporary, $vineflowerUrl)
            if ($result.ExitCode -ne 0) {
                throw "curl failed to download Vineflower: $($result.Output -join ' ')"
            }
        }
        $hash = Get-Sha256 -Path $temporary
        if ($hash -ne $vineflowerSha256) {
            throw "Downloaded Vineflower SHA-256 mismatch: expected $vineflowerSha256, got $hash"
        }
        if (Test-Path -LiteralPath $target) {
            Remove-Item -LiteralPath $target -Force
        }
        Move-Item -LiteralPath $temporary -Destination $target -Force
        return $target
    }
    finally {
        if (Test-Path -LiteralPath $temporary) {
            Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
        }
    }
}

$jdk = Get-Jdk
if (Test-Path -LiteralPath $classes) {
    Remove-Item -LiteralPath $classes -Recurse -Force
}
New-Item -ItemType Directory -Path $classes -Force | Out-Null
New-Item -ItemType Directory -Path $out -Force | Out-Null

$sourceDirectory = Join-Path $root "src"
$sources = @(Get-ChildItem -LiteralPath $sourceDirectory -Recurse -Filter *.java |
    Sort-Object FullName |
    ForEach-Object { $_.FullName })
if ($sources.Count -eq 0) {
    throw "No sourcepatcher Java sources found under $sourceDirectory."
}

$compileArguments = @("--release", "25", "-proc:none", "-encoding", "UTF-8", "-d", $classes) + $sources
$compile = Invoke-Native -FilePath $jdk.Javac -Arguments $compileArguments
$buildLog = Join-Path $out "build.log"
Write-Utf8NoBom -Path $buildLog -Text ((@($jdk.Version) + $compile.Output + @("")) -join "`n")
if ($compile.ExitCode -ne 0) {
    throw "javac failed for sourcepatcher helpers:`n$($compile.Output -join "`n")"
}

$vineflower = Resolve-Vineflower -Explicit $VineflowerJar

Write-Utf8NoBom -Path (Join-Path $out "jdk-path.txt") -Text ($jdk.Path + "`n")
Write-Utf8NoBom -Path (Join-Path $out "vineflower-path.txt") -Text ($vineflower + "`n")
Write-Utf8NoBom -Path (Join-Path $out "sourcepatcher-classpath.txt") -Text ($classes + "`n")

Write-Host "SOURCE PATCHER BUILD ok=true classes=$classes vineflower=$vineflower jdk=$($jdk.Path)"
