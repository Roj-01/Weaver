param(
    [string]$Tag = "v2.3.2",
    [string]$OutDir = ""
)
$ErrorActionPreference = "Stop"

# Builds the tester package: Weaver's agent and libraries, the First Mod
# example, the launcher, get-game.ps1 and play.ps1, docs, licenses, and the
# source as a git bundle of $Tag. No Minecraft file goes in.

$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $OutDir) { $OutDir = Join-Path $repo "out\release" }
$name = "Weaver-" + $Tag.TrimStart("v")
$package = Join-Path $OutDir $name
$zip = Join-Path $OutDir ($name + ".zip")
$agentDir = Join-Path $repo "core\build\agent"
$modJar = Join-Path $repo "examples\first-mod\build\libs\First Mod.jar"
foreach ($required in @((Join-Path $agentDir "weaver-agent.jar"), $modJar)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "missing $required; run .\gradlew.bat :core:agentJar and .\gradlew.bat -p examples/first-mod weaverBuild"
    }
}

if (Test-Path $package) { Remove-Item -LiteralPath $package -Recurse -Force }
if (Test-Path $zip) { Remove-Item -LiteralPath $zip -Force }
foreach ($dir in @("weaver\agent\lib", "mods", "tools\launcher", "docs", "source")) {
    New-Item -ItemType Directory -Path (Join-Path $package $dir) -Force | Out-Null
}

Copy-Item -LiteralPath (Join-Path $agentDir "weaver-agent.jar") -Destination (Join-Path $package "weaver\agent")
Copy-Item -Path (Join-Path $agentDir "lib\*.jar") -Destination (Join-Path $package "weaver\agent\lib")
Copy-Item -LiteralPath $modJar -Destination (Join-Path $package "mods\firstmod-1.0.0.jar")
Copy-Item -LiteralPath (Join-Path $repo "tools\launcher\launch.ps1") -Destination (Join-Path $package "tools\launcher")
Copy-Item -LiteralPath (Join-Path $repo "tools\jdk.ps1") -Destination (Join-Path $package "tools")
foreach ($file in @("get-game.ps1", "play.ps1", "README.txt", "THIRD-PARTY.txt")) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "preview\$file") -Destination $package
}
Copy-Item -LiteralPath (Join-Path $repo "LICENSE") -Destination $package
Copy-Item -LiteralPath (Join-Path $repo "NOTICE") -Destination $package
Copy-Item -LiteralPath (Join-Path $repo "README-LICENSE.md") -Destination $package

# A shipped package must carry the LGPL-2.1-only text and its explanation,
# not a placeholder.
$licenseText = Get-Content -LiteralPath (Join-Path $package "LICENSE") -Raw
if ($licenseText -notmatch 'GNU LESSER GENERAL PUBLIC LICENSE') {
    throw "LICENSE is not the GNU Lesser General Public License v2.1 text"
}
if (-not (Test-Path -LiteralPath (Join-Path $package "README-LICENSE.md") -PathType Leaf)) {
    throw "README-LICENSE.md is missing from the package"
}
if (-not (Test-Path -LiteralPath (Join-Path $package "NOTICE") -PathType Leaf)) {
    throw "NOTICE is missing from the package"
}
foreach ($doc in @("release-notes.md", "getting-started.md")) {
    Copy-Item -LiteralPath (Join-Path $repo "docs\$doc") -Destination (Join-Path $package "docs")
}

$bundle = Join-Path $package "source\weaver-$($Tag).bundle"
# git prints progress and "is okay" on stderr, which Windows PowerShell turns
# into errors under "Stop"; check the exit codes instead.
$ErrorActionPreference = "Continue"
& git -C $repo bundle create $bundle $Tag main 2>$null | Out-Null
$created = $LASTEXITCODE
& git -C $repo bundle verify $bundle 2>$null | Out-Null
$verified = $LASTEXITCODE
$ErrorActionPreference = "Stop"
if ($created -ne 0) { throw "git bundle failed" }
if ($verified -ne 0) { throw "git bundle verify failed" }

# Windows PowerShell's Compress-Archive writes "\" in entry names; write "/"
# so the zip opens the same everywhere.
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($zip, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $prefix = (Split-Path -Parent $package).Length + 1
    foreach ($file in Get-ChildItem -LiteralPath $package -Recurse -File) {
        $entry = $file.FullName.Substring($prefix).Replace('\', '/')
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $entry,
            [System.IO.Compression.CompressionLevel]::Optimal)
    }
} finally {
    $archive.Dispose()
}
$size = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host "PREVIEW PACKAGE ok=true zip=$zip size=${size}MB"
