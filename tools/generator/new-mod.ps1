param(
    [Parameter(Mandatory = $true)]
    [string]$Name,
    [Parameter(Mandatory = $true)]
    [string]$Target,
    [string]$Package = "",
    [string]$MainClass = "StarterMod"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$template = Join-Path $root "examples\starter"
if (-not (Test-Path $template)) { throw "starter template missing: $template" }
if (Test-Path $Target) { throw "target already exists: $Target" }

$id = ($Name.ToLowerInvariant() -replace '[^a-z0-9_-]+', '-').Trim('-')
if (-not $id) { throw "name has no usable characters" }
if (-not $Package) { $Package = "net.weaver.$($id -replace '-', '.')" }
$packagePath = $Package -replace '\.', '\'

New-Item -ItemType Directory -Path $Target -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $template "settings.gradle.kts") -Destination $Target -Force
Copy-Item -LiteralPath (Join-Path $template "build.gradle.kts") -Destination $Target -Force
Copy-Item -LiteralPath (Join-Path $template "src") -Destination $Target -Recurse -Force

$oldJava = Join-Path $Target "src\main\java\net\weaver\starter\StarterMod.java"
$newJava = Join-Path $Target "src\main\java\$packagePath\$MainClass.java"
New-Item -ItemType Directory -Path (Split-Path -Parent $newJava) -Force | Out-Null
$text = [IO.File]::ReadAllText($oldJava)
$text = $text.Replace("package net.weaver.starter;", "package $Package;")
$text = $text.Replace("class StarterMod", "class $MainClass")
$text = $text.Replace("StarterMod", $MainClass)
[IO.File]::WriteAllText($newJava, $text, (New-Object System.Text.UTF8Encoding($false)))
Remove-Item -LiteralPath $oldJava -Force
Remove-Item -LiteralPath (Join-Path $Target "src\main\java\net\weaver\starter") -Recurse -Force -ErrorAction SilentlyContinue

foreach ($file in @((Join-Path $Target "settings.gradle.kts"), (Join-Path $Target "build.gradle.kts"))) {
    $content = [IO.File]::ReadAllText($file)
    $content = $content.Replace("weaver-starter", $id)
    $content = $content.Replace("Weaver Starter", $Name)
    $content = $content.Replace("net.weaver.starter", $Package)
    $content = $content.Replace("StarterMod", $MainClass)
    [IO.File]::WriteAllText($file, $content, (New-Object System.Text.UTF8Encoding($false)))
}

Write-Host "GENERATOR ok=true target=$Target id=$id package=$Package main=$MainClass"
