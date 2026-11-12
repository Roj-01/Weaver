param(
    [string]$GameDir = "",
    [string]$Version = "26.3",
    [int]$Parallel = 16
)
$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# Downloads Minecraft (client jar, libraries, natives, assets) from Mojang's
# own servers into .\game, the same layout Weaver's launcher uses. Needs only
# Windows 10/11 (curl.exe is built in) and an internet connection. Files that
# are already complete are skipped, so it can be run again after a failure.

$root = $PSScriptRoot
if (-not $GameDir) { $GameDir = Join-Path $root "game" }
$GameDir = [System.IO.Path]::GetFullPath($GameDir)
$curl = Join-Path $env:SystemRoot "System32\curl.exe"
if (-not (Test-Path $curl)) { throw "curl.exe not found ($curl); Windows 10 1803 or later is needed" }
$arm = $env:PROCESSOR_ARCHITECTURE -eq "ARM64"
$nativeClassifier = if ($arm) { "natives-windows-arm64" } else { "natives-windows" }
New-Item -ItemType Directory -Path $GameDir -Force | Out-Null

function Get-Json([string]$Url) {
    return Invoke-RestMethod -Uri $Url -UseBasicParsing
}

function Test-Allowed($rules) {
    if (-not $rules) { return $true }
    $allowed = $false
    foreach ($rule in $rules) {
        if ($rule.features) { continue }
        $os = $rule.os
        $applies = (-not $os) -or (($os.name -eq $null -or $os.name -eq "windows") -and
            ($os.arch -eq $null -or ($arm -and $os.arch -eq "arm64") -or (-not $arm -and $os.arch -ne "arm64")))
        if ($applies) { $allowed = $rule.action -eq "allow" }
    }
    return $allowed
}

Write-Host "GET GAME version=$Version into $GameDir"
$manifest = Get-Json "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
$entry = $manifest.versions | Where-Object { $_.id -eq $Version } | Select-Object -First 1
if (-not $entry) { throw "Minecraft $Version is not in Mojang's version list" }
$versionJsonPath = Join-Path $GameDir "version.json"
Invoke-WebRequest -Uri $entry.url -OutFile $versionJsonPath -UseBasicParsing
$info = Get-Content -LiteralPath $versionJsonPath -Raw | ConvertFrom-Json

$jobs = New-Object System.Collections.Generic.List[object]
function Add-Job([string]$Url, [string]$Path, [string]$Sha1, [long]$Size) {
    $jobs.Add([pscustomobject]@{ Url = $Url; Path = $Path; Sha1 = $Sha1; Size = $Size })
}

$clientJar = Join-Path $GameDir "client.jar"
Add-Job $info.downloads.client.url $clientJar $info.downloads.client.sha1 $info.downloads.client.size

$classpath = New-Object System.Collections.Generic.List[string]
$classpath.Add($clientJar)
$nativeJars = New-Object System.Collections.Generic.List[string]
foreach ($library in $info.libraries) {
    if (-not (Test-Allowed $library.rules)) { continue }
    $artifact = $library.downloads.artifact
    if (-not $artifact) { continue }
    $parts = $library.name -split ":"
    $classifier = if ($parts.Count -ge 4) { $parts[3] } else { "" }
    if ($classifier -like "natives-*" -and $classifier -ne $nativeClassifier) { continue }
    $path = Join-Path (Join-Path $GameDir "libraries") ($artifact.path -replace "/", "\")
    Add-Job $artifact.url $path $artifact.sha1 $artifact.size
    $classpath.Add($path)
    if ($classifier -eq $nativeClassifier) { $nativeJars.Add($path) }
}

$assets = Join-Path $GameDir "assets"
$indexPath = Join-Path $assets ("indexes\" + $info.assetIndex.id + ".json")
New-Item -ItemType Directory -Path (Split-Path -Parent $indexPath) -Force | Out-Null
Invoke-WebRequest -Uri $info.assetIndex.url -OutFile $indexPath -UseBasicParsing
$index = Get-Content -LiteralPath $indexPath -Raw | ConvertFrom-Json
foreach ($object in $index.objects.PSObject.Properties) {
    $hash = $object.Value.hash
    $prefix = $hash.Substring(0, 2)
    Add-Job "https://resources.download.minecraft.net/$prefix/$hash" (Join-Path $assets "objects\$prefix\$hash") $hash $object.Value.size
}

# Skip files that are already complete.
$missing = @($jobs | Where-Object { -not (Test-Path -LiteralPath $_.Path) -or (Get-Item -LiteralPath $_.Path).Length -ne $_.Size })
Write-Host "GET GAME files=$($jobs.Count) to-download=$($missing.Count)"
if ($missing.Count -gt 0) {
    $config = Join-Path $GameDir "downloads.curl"
    $lines = foreach ($job in $missing) {
        "url = `"$($job.Url)`""
        "output = `"$($job.Path -replace '\\', '/')`""
    }
    [System.IO.File]::WriteAllLines($config, [string[]]$lines)
    & $curl --parallel --parallel-max $Parallel --create-dirs --fail --retry 3 --silent --show-error --config $config
    if ($LASTEXITCODE -ne 0) { throw "download failed (curl exit $LASTEXITCODE); run get-game.ps1 again to resume" }
    Remove-Item -LiteralPath $config -Force
}

# The jars must match Mojang's checksums.
foreach ($job in @($jobs | Where-Object { $_.Path -like "*.jar" })) {
    $actual = (Get-FileHash -LiteralPath $job.Path -Algorithm SHA1).Hash.ToLowerInvariant()
    if ($actual -ne $job.Sha1) {
        Remove-Item -LiteralPath $job.Path -Force
        throw "checksum mismatch for $($job.Path); it was removed, run get-game.ps1 again"
    }
}

# Native libraries go where the launcher points java.library.path.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$nativesJava = Join-Path $GameDir "natives\java"
foreach ($dir in @($nativesJava, (Join-Path $GameDir "natives\jna"), (Join-Path $GameDir "natives\lwjgl"), (Join-Path $GameDir "natives\netty"))) {
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
}
foreach ($jar in $nativeJars) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
    try {
        foreach ($item in $zip.Entries) {
            if ($item.FullName -like "META-INF/*" -or -not $item.Name.EndsWith(".dll")) { continue }
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($item, (Join-Path $nativesJava $item.Name), $true)
        }
    } finally {
        $zip.Dispose()
    }
}

[System.IO.File]::WriteAllText((Join-Path $GameDir "classpath.txt"),
    (($classpath | ForEach-Object { $_ -replace '\\', '/' }) -join ";"))
Write-Host "GET GAME ok=true version=$Version libraries=$($classpath.Count - 1) assets=$($index.objects.PSObject.Properties.Name.Count) dir=$GameDir"
