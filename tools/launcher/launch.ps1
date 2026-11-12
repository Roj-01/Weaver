param(
    [ValidateSet("client", "server")]
    [string]$Side = "client",
    # Maximum Java heap for the game (-Xmx); WEAVER_MEMORY overrides the default.
    [string]$Memory = $(if ($env:WEAVER_MEMORY) { $env:WEAVER_MEMORY } else { "8G" }),
    [string]$GameDir = $env:WEAVER_GAME_DIR,
    [string]$ServerDir = $env:WEAVER_SERVER_DIR,
    [string]$PatchedClientJar = "",
    [string]$PatchedServerJar = "",
    [string]$AgentJar = "",
    [string]$ModsDir = "",
    [string]$ConfigDir = "",
    [string]$DataDir = "",
    [string]$Log = "",
    [string]$Markers = "",
    [string]$Username = "WeaverProof",
    # Client only: the offline player UUID; the default is the proofs' fixed one.
    [string]$Uuid = "da816c73-37ef-3826-bda4-5ae589e5ac56",
    [string]$QuickPlayWorld = "",
    [string]$QuickPlayServer = "",
    # Dedicated server only: the world folder name (passed as --world).
    [string]$World = "",
    [int]$Port = 25566,
    [int]$TimeoutSeconds = 90,
    [string]$WaitFor = "",
    # Kept for older scripts: a client always runs with its integrated server's hooks now.
    [switch]$IntegratedServer,
    # Client only: leave out the integrated server's hooks (for a client that only joins remote servers).
    [switch]$NoIntegratedServer,
    [switch]$SharedClassSpace,
    [switch]$Interactive,
    # Show the client window. By default a non-interactive client starts
    # minimized: on some GPU drivers a visible window makes the game crash
    # natively at startup far more often (see HANDOFF.md). WEAVER_CLIENT_VISIBLE=1
    # has the same effect.
    [switch]$Visible,
    [switch]$KeepRunning,
    [switch]$StayAlive,
    [switch]$Debug,
    [ValidateRange(1, 65535)]
    [int]$DebugPort = 5005,
    [switch]$DebugSuspend,
    # Developer hot swap: class output folders (separated by ';') whose recompiled
    # mod classes replace the running code (sets -Dweaver.dev.hotswap).
    [string]$HotSwapDirs = "",
    # Resource folders (assets/data) that reload live with the hot reload, separated by ';'.
    [string]$HotSwapResources = "",
    [switch]$DryRun
)

$hotSwapArguments = @()
if ($HotSwapDirs) {
    $hotSwapFolders = @($HotSwapDirs -split ';' | Where-Object { $_ } | ForEach-Object { [System.IO.Path]::GetFullPath($_) })
    $hotSwapArguments = @("-Dweaver.dev.hotswap=true", "-Dweaver.dev.hotswap.dirs=$($hotSwapFolders -join ';')")
}
if ($HotSwapResources) {
    $resourceFolders = @($HotSwapResources -split ';' | Where-Object { $_ } | ForEach-Object { [System.IO.Path]::GetFullPath($_) })
    if (-not $HotSwapDirs) { $hotSwapArguments += "-Dweaver.dev.hotswap=true" }
    $hotSwapArguments += "-Dweaver.dev.hotswap.resources=$($resourceFolders -join ';')"
}

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $AgentJar) { $AgentJar = Join-Path $root "core\build\agent\weaver-agent.jar" }
if (-not $ModsDir) { $ModsDir = Join-Path $root "mods" }
if (-not $ConfigDir) { $ConfigDir = Join-Path $root "run\config" }
if (-not $DataDir) { $DataDir = Join-Path $root "run\data" }
if (-not $Log) { $Log = Join-Path $root "run\logs\$Side.log" }
if (-not $Markers) { $Markers = Join-Path $root "run\logs\$Side-markers.log" }
$Log = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Log)
# A JVM crash report (hs_err) goes next to the log instead of the game directory.
$crashFile = Join-Path (Split-Path -Parent $Log) "hs_err_pid%p.log"
if (-not $PatchedClientJar) { $PatchedClientJar = $env:WEAVER_PATCHED_CLIENT_JAR }
if (-not $PatchedServerJar) { $PatchedServerJar = $env:WEAVER_PATCHED_SERVER_JAR }

$patched = $false
$patchedJar = ""

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + $candidates + @("C:\Program Files\Java\jdk-26.0.1")
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "bin\java.exe")) { return $candidate }
    }
    throw "No JDK 26 found. Set JAVA_HOME."
}

function Quote-Path {
    param([string]$Path)
    if ($Path -match '\s') { return "`"$Path`"" }
    return $Path
}

$jdk = Find-Jdk
$java = Join-Path $jdk "bin\java.exe"

$sharedMode = $SharedClassSpace.IsPresent -or $env:WEAVER_SHARED_CLASS_SPACE -eq "true"
$debugSuspendValue = if ($DebugSuspend) { "y" } else { "n" }

if (-not (Test-Path $AgentJar)) { throw "agent jar missing: $AgentJar" }
New-Item -ItemType Directory -Path (Split-Path -Parent $Log) -Force | Out-Null
New-Item -ItemType Directory -Path $ConfigDir -Force | Out-Null
New-Item -ItemType Directory -Path $DataDir -Force | Out-Null
Remove-Item -LiteralPath $Log, $Markers -Force -ErrorAction SilentlyContinue

# A client hosts its single-player worlds itself, so the server-side hooks (ticks,
# player and entity joins, ...) must be applied in the client too, matching the
# agent's own default. Without them, server events never fire in single player.
$integrated = ($Side -eq "client") -and -not $NoIntegratedServer
$agentArg = "-javaagent:$AgentJar=side=$Side;integratedServer=$($integrated.ToString().ToLower());modsDir=$ModsDir;configDir=$ConfigDir;dataDir=$DataDir;markersFile=$Markers"

if ($Side -eq "client") {
    if (-not $GameDir) { throw "GameDir is required for the client" }
    $classpathFile = Join-Path $GameDir "classpath.txt"
    if (-not (Test-Path $classpathFile)) { throw "missing game classpath: $classpathFile" }
    $classpath = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
    # classpath.txt holds absolute paths from tools/fetch-game; rebase stale
    # entries onto the current game directory so a moved checkout still launches.
    $gameRoot = [System.IO.Path]::GetFullPath($GameDir)
    $missingEntries = 0
    $rebasedEntries = foreach ($entry in ($classpath -split ';')) {
        $entry = $entry.Trim()
        if ($entry -eq '' -or (Test-Path -LiteralPath $entry)) { $entry; continue }
        $normalized = $entry -replace '\\', '/'
        $librariesAt = $normalized.LastIndexOf('/libraries/')
        $candidate = $null
        if ($librariesAt -ge 0) {
            $candidate = Join-Path $gameRoot $normalized.Substring($librariesAt + 1)
        } elseif ($normalized.EndsWith('/client.jar')) {
            $candidate = Join-Path $gameRoot 'client.jar'
        }
        if ($candidate -and (Test-Path -LiteralPath $candidate)) { $candidate } else { $missingEntries++; $entry }
    }
    if ($missingEntries -gt 0) {
        Write-Warning "$missingEntries game classpath entries are missing; rerun tools/fetch-game/fetch-game.sh"
    }
    $classpath = ($rebasedEntries | Where-Object { $_ -ne '' }) -join ';'
    if ($PatchedClientJar) {
        if (-not (Test-Path -LiteralPath $PatchedClientJar -PathType Leaf)) {
            throw "patched client jar missing: $PatchedClientJar"
        }
        $patchedJar = [System.IO.Path]::GetFullPath($PatchedClientJar)
        $entries = $classpath -split ';'
        $target = -1
        for ($i = 0; $i -lt $entries.Length; $i++) {
            $leaf = [System.IO.Path]::GetFileName($entries[$i].Trim())
            if ($leaf -ieq "client.jar" -or $leaf -match '^client[-_].*\.jar$') {
                $target = $i
                break
            }
        }
        if ($target -lt 0) { $target = 0 }
        $entries[$target] = $patchedJar
        $classpath = $entries -join ';'
        $patched = $true
    }
    $natives = Join-Path $GameDir "natives"
    $assets = Join-Path $GameDir "assets"
    $jvmArguments = @(
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        "-Xmx$Memory",
        # Vanilla 26.3 passes this (version.json); without it native code (drivers,
        # LWJGL) can overflow the thread stack and kill the JVM with no hs_err file.
        "-XX:StackShadowPages=32",
        "-XX:ErrorFile=$crashFile",
        "-Djava.library.path=$natives/java",
        "-Djna.tmpdir=$natives/jna",
        "-Dorg.lwjgl.system.SharedLibraryExtractPath=$natives/lwjgl",
        "-Dio.netty.native.workdir=$natives/netty",
        "--enable-native-access=ALL-UNNAMED",
        "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED"
    )
    $jvmArguments += $hotSwapArguments
    if ($Debug) {
        $jvmArguments += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=$debugSuspendValue,address=*:$DebugPort"
    }
    $gameArguments = @(
        "--username", $Username,
        "--version", "26.3",
        "--gameDir", $GameDir,
        "--assetsDir", $assets,
        "--assetIndex", "34",
        "--uuid", $Uuid,
        "--accessToken", "weaver-offline-0",
        "--xuid", "0",
        "--versionType", "release"
    )
    if ($QuickPlayWorld) {
        $gameArguments += @("--quickPlaySingleplayer", "`"$QuickPlayWorld`"")
    }
    if ($QuickPlayServer) {
        $gameArguments += @("--quickPlayMultiplayer", $QuickPlayServer)
    }
    if ($sharedMode) {
        if ($patched) {
            $sharedClasspathDir = [System.IO.Path]::GetDirectoryName(
                [System.IO.Path]::GetFullPath($Log))
            $sharedClasspathFile = Join-Path $sharedClasspathDir "shared-classpath.txt"
            [System.IO.File]::WriteAllText($sharedClasspathFile, $classpath,
                (New-Object System.Text.UTF8Encoding -ArgumentList $false))
        } else {
            $sharedClasspathFile = [System.IO.Path]::GetFullPath($classpathFile)
        }
        $arguments = $jvmArguments + @(
            "-Dweaver.classspace.shared=true",
            "-Dweaver.shared.main=net.minecraft.client.main.Main",
            "-Dweaver.shared.classpathFile=$(Quote-Path $sharedClasspathFile)",
            "-cp", (Quote-Path $AgentJar),
            $agentArg,
            "net.weaver.core.SharedMain"
        ) + $gameArguments
    } else {
        $arguments = $jvmArguments + @("-cp", $classpath, $agentArg,
            "net.minecraft.client.main.Main") + $gameArguments
    }
} else {
    if (-not $ServerDir) { throw "ServerDir is required for the server" }
    $serverJar = Join-Path $ServerDir "versions\26.3\server-26.3.jar"
    if (-not (Test-Path $serverJar)) { throw "missing real server jar: $serverJar" }
    if ($PatchedServerJar) {
        if (-not (Test-Path -LiteralPath $PatchedServerJar -PathType Leaf)) {
            throw "patched server jar missing: $PatchedServerJar"
        }
        $patchedJar = [System.IO.Path]::GetFullPath($PatchedServerJar)
        $patched = $true
    }
    $libraries = Get-ChildItem -LiteralPath (Join-Path $ServerDir "libraries") -Recurse -Filter *.jar |
        ForEach-Object { $_.FullName }
    $classpathEntries = @($libraries) + @($serverJar)
    if ($patched) { $classpathEntries[$classpathEntries.Count - 1] = $patchedJar }
    $classpath = $classpathEntries -join ";"
    $jvmArguments = @(
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        "-Xmx$Memory",
        "-XX:ErrorFile=$crashFile",
        "--enable-native-access=ALL-UNNAMED"
    )
    $jvmArguments += $hotSwapArguments
    if ($Debug) {
        $jvmArguments += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=$debugSuspendValue,address=*:$DebugPort"
    }
    $gameArguments = @("nogui", "--port", "$Port")
    if ($World) { $gameArguments += @("--world", $World) }
    if ($sharedMode) {
        $logDir = [System.IO.Path]::GetFullPath((Split-Path -Parent $Log))
        $sharedClasspathFile = Join-Path $logDir "shared-classpath.txt"
        [System.IO.File]::WriteAllText($sharedClasspathFile, $classpath,
            (New-Object System.Text.UTF8Encoding -ArgumentList $false))
        $arguments = $jvmArguments + @(
            "-Dweaver.shared.classpathFile=$(Quote-Path $sharedClasspathFile)",
            "-Dweaver.shared.main=net.minecraft.server.Main",
            "-Dweaver.classspace.shared=true",
            "-cp", (Quote-Path $AgentJar),
            $agentArg,
            "net.weaver.core.SharedMain"
        ) + $gameArguments
    } else {
        $arguments = $jvmArguments + @("-cp", $classpath, $agentArg,
            "net.minecraft.server.Main") + $gameArguments
    }
}

$workdir = if ($Side -eq "client") { $GameDir } else { $ServerDir }
if ($patched) {
    Write-Host "LAUNCH PATCH side=$Side patched=true jar=$patchedJar"
} else {
    Write-Host "LAUNCH PATCH side=$Side patched=false"
}
if ($sharedMode) {
    Write-Host "WARNING: shared class space is opt-in and experimental; it requires the net.weaver.core.SharedMain bootstrap."
    Write-Host "WARNING: shared mode passes the game classpath to the agent by file and hosts game and mod classes on the shared loader; the default system mode is unchanged."
}
Write-Host "LAUNCH side=$Side shared=$($sharedMode.ToString().ToLower()) workdir=$workdir log=$Log"
if ($Debug) {
    Write-Host "LAUNCH DEBUG side=$Side port=$DebugPort suspend=$debugSuspendValue"
}
if ($DryRun) {
    Write-Host "DRY RUN $java $($arguments -join ' ')"
    exit 0
}
if ($Interactive) {
    Push-Location -LiteralPath $workdir
    try {
        & $java @arguments
    } finally {
        Pop-Location
    }
    exit $LASTEXITCODE
}
# Windows PowerShell joins -ArgumentList without quoting: quote every argument
# with whitespace (paths under folders such as "My Games") so it stays one argument.
$startArguments = @($arguments | ForEach-Object {
    if ($_ -match '\s' -and $_ -notmatch '"') { '"' + $_ + '"' } else { $_ }
})
if ($KeepRunning) {
    $process = Start-Process -FilePath $java -ArgumentList $startArguments -WorkingDirectory $workdir `
        -RedirectStandardOutput $Log -RedirectStandardError "$Log.err" -PassThru `
        -WindowStyle Minimized
} elseif ($Side -eq "client" -and -not $Visible -and $env:WEAVER_CLIENT_VISIBLE -ne "1") {
    # The game window takes the console window's show state, so it opens minimized.
    Write-Host "LAUNCH WINDOW minimized (use -Visible or WEAVER_CLIENT_VISIBLE=1 to show it)"
    $process = Start-Process -FilePath $java -ArgumentList $startArguments -WorkingDirectory $workdir `
        -RedirectStandardOutput $Log -RedirectStandardError "$Log.err" -PassThru `
        -WindowStyle Minimized
} else {
    $process = Start-Process -FilePath $java -ArgumentList $startArguments -WorkingDirectory $workdir `
        -RedirectStandardOutput $Log -RedirectStandardError "$Log.err" -PassThru -NoNewWindow
}

# Windows PowerShell only reports ExitCode when the handle was read while the process ran.
$null = $process.Handle
$launchStart = Get-Date
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$marker = $false
if ($WaitFor) {
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $Log) {
            $text = Get-Content -LiteralPath $Log -Raw -ErrorAction SilentlyContinue
            if ($text -match $WaitFor) {
                $marker = $true
                break
            }
        }
        if ($process.HasExited) {
            break
        }
        Start-Sleep -Milliseconds 500
    }
}

# Developer hot reload: a game that restarted itself to apply a change left a note
# saying where to come back to; start it again there with the same options.
$restartNote = if ($DataDir) { Join-Path $DataDir "dev-restart.json" } else { "" }
if (-not $marker -and $process.HasExited -and $restartNote -and (Test-Path -LiteralPath $restartNote)) {
    $note = Get-Content -LiteralPath $restartNote -Raw | ConvertFrom-Json
    Remove-Item -LiteralPath $restartNote -Force
    $restarts = [int]$env:WEAVER_DEV_RESTARTS + 1
    if ($restarts -gt 20) { throw "dev restart: more than 20 restarts in a row" }
    $env:WEAVER_DEV_RESTARTS = "$restarts"
    if (Test-Path -LiteralPath $Log) { Copy-Item -LiteralPath $Log -Destination "$Log.before-restart-$restarts" -Force }
    $again = @{}
    foreach ($key in $PSBoundParameters.Keys) { $again[$key] = $PSBoundParameters[$key] }
    $again.Remove("QuickPlayWorld"); $again.Remove("QuickPlayServer")
    if ($note.quickPlayWorld) { $again["QuickPlayWorld"] = [string]$note.quickPlayWorld }
    if ($note.quickPlayServer) { $again["QuickPlayServer"] = [string]$note.quickPlayServer }
    $left = [int][Math]::Max(30, ($deadline - (Get-Date)).TotalSeconds)
    $again["TimeoutSeconds"] = $left
    Write-Host "LAUNCH DEV RESTART n=$restarts world=$($note.quickPlayWorld) server=$($note.quickPlayServer)"
    & $PSCommandPath @again
    exit $LASTEXITCODE
}

if ($KeepRunning) {
    if ($process.HasExited) {
        Write-Host "LAUNCH RESULT side=$Side ok=false exited=true log=$Log"
        if (Test-Path $Log) { Get-Content -LiteralPath $Log -Tail 80 }
        if (Test-Path "$Log.err") { Get-Content -LiteralPath "$Log.err" -Tail 40 }
        exit 1
    }
    if (-not $WaitFor -or $marker) {
        Write-Host "LAUNCH RESULT side=$Side ok=true marker=$($marker.ToString().ToLower()) pid=$($process.Id) log=$Log"
        if ($StayAlive) { $process.WaitForExit(); exit $process.ExitCode }
        exit 0
    }
    Write-Host "LAUNCH RESULT side=$Side ok=false marker=false pid=$($process.Id) log=$Log"
    exit 1
}

if (-not $process.HasExited) {
    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 500
}

if ($marker) {
    Write-Host "LAUNCH RESULT side=$Side ok=true marker=true log=$Log"
    exit 0
}
if ($process.HasExited) {
    $code = $process.ExitCode
    Write-Host ("LAUNCH EXIT side=$Side code=$code hex=0x{0:X8}" -f $code)
    $crashes = Get-ChildItem -LiteralPath (Split-Path -Parent $Log) -Filter "hs_err_pid*.log" -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -gt $launchStart }
    foreach ($crash in $crashes) { Write-Host "LAUNCH CRASH REPORT $($crash.FullName)" }
}
Write-Host "LAUNCH RESULT side=$Side ok=false marker=false log=$Log"
if (Test-Path $Log) { Get-Content -LiteralPath $Log -Tail 80 }
if (Test-Path "$Log.err") { Get-Content -LiteralPath "$Log.err" -Tail 40 }
exit 1
