#requires -Version 5.1
[CmdletBinding()]
param()

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $root 'gradlew.bat'
$captureDir = Join-Path ([System.IO.Path]::GetTempPath()) ("weaver-verify-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $captureDir -Force | Out-Null

$script:failed = $false
$script:ranSteps = New-Object System.Collections.Generic.List[string]
$script:skippedSteps = New-Object System.Collections.Generic.List[string]

function Write-StepResult {
    param(
        [Parameter(Mandatory = $true)][string]$Step,
        [Parameter(Mandatory = $true)][string]$Status,
        [Parameter(Mandatory = $true)][string]$Detail
    )
    $color = 'Red'
    if ($Status -eq 'PASS') { $color = 'Green' }
    if ($Status -eq 'SKIP') { $color = 'Yellow' }
    Write-Host ("[{0}] {1}: {2}" -f $Status, $Step, $Detail) -ForegroundColor $color
    if ($Status -eq 'FAIL') { $script:failed = $true }
}

function Invoke-CapturedCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string[]]$CommandArguments
    )
    $logPath = Join-Path $captureDir ($Name + '.log')
    Push-Location -LiteralPath $root
    try {
        $previousErrorAction = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $gradlew @CommandArguments > $logPath 2>&1
        $exitCode = $LASTEXITCODE
        $ErrorActionPreference = $previousErrorAction
    } finally {
        Pop-Location
    }
    $output = ''
    if (Test-Path -LiteralPath $logPath -PathType Leaf) {
        $raw = Get-Content -LiteralPath $logPath -Raw
        if ($null -ne $raw) { $output = $raw }
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = $output
        LogPath = $logPath
    }
}

function Invoke-CapturedScript {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$ScriptPath,
        [string[]]$ScriptArguments = @()
    )
    $logPath = Join-Path $captureDir ($Name + '.log')
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $ScriptPath @ScriptArguments > $logPath 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    $output = ''
    if (Test-Path -LiteralPath $logPath -PathType Leaf) {
        $raw = Get-Content -LiteralPath $logPath -Raw
        if ($null -ne $raw) { $output = $raw }
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = $output
        LogPath = $logPath
    }
}

function Show-FailureOutput {
    param([Parameter(Mandatory = $true)]$Result)
    $tail = @(($Result.Output -split "`r?`n")) | Select-Object -Last 60
    Write-Host ("  log: " + $Result.LogPath)
    foreach ($line in $tail) { Write-Host ("  | " + $line) }
}

function Get-ApiGameJars {
    param([Parameter(Mandatory = $true)][string]$Directory)
    if (-not (Test-Path -LiteralPath $Directory -PathType Container)) { return @() }
    return @(Get-ChildItem -LiteralPath $Directory -File |
        Where-Object { $_.Name -like 'api-game-*.jar' } |
        ForEach-Object { $_.Name })
}

function Get-AgentClassPathEntries {
    $jarPath = Join-Path $root 'core\build\agent\weaver-agent.jar'
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) { return $null }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        $entry = $zip.GetEntry('META-INF/MANIFEST.MF')
        if ($null -eq $entry) { return $null }
        $reader = New-Object System.IO.StreamReader($entry.Open())
        try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally {
        $zip.Dispose()
    }
    $unfolded = $text -replace "`r`n ", '' -replace "`n ", ''
    $classPathLine = @($unfolded -split "`r?`n" |
        Where-Object { $_ -like 'Class-Path:*' } |
        Select-Object -First 1)
    if ($classPathLine.Count -eq 0) { return @() }
    $value = $classPathLine[0].Substring('Class-Path:'.Length).Trim()
    return @($value -split '\s+' | Where-Object { $_ -ne '' })
}

function Get-GameClasspathStatus {
    $gameDir = $env:WEAVER_GAME_DIR
    if ([string]::IsNullOrWhiteSpace($gameDir)) {
        $gameDir = Join-Path $root 'work\game-26.3'
    }
    $classpathFile = Join-Path $gameDir 'classpath.txt'
    if (Test-Path -LiteralPath $classpathFile -PathType Leaf) {
        return [pscustomobject]@{ Present = $true; Source = $classpathFile }
    }
    $serverJar = Join-Path $root 'work\server-26.3\versions\26.3\server-26.3.jar'
    if (Test-Path -LiteralPath $serverJar -PathType Leaf) {
        return [pscustomobject]@{ Present = $true; Source = $serverJar }
    }
    return [pscustomobject]@{ Present = $false; Source = '' }
}

Write-Host "Weaver verify (root: $root)"
Write-Host ("Captured logs: " + $captureDir)

try {
    $script:ranSteps.Add('1')
    Write-Host ''
    Write-Host '== Step 1: no-game build (forceNoGameApi)'
    $noGameArgs = @(
        'clean', 'build', '--no-build-cache', '--rerun-tasks', '--no-daemon',
        '-Pweaver.forceNoGameApi=true'
    )
    $noGame = Invoke-CapturedCommand -Name 'step1-no-game' -CommandArguments $noGameArgs
    $noGameSkipLine = $noGame.Output.Contains('[weaver] api-game SKIPPED reason=no-game-classpath')
    $noGameJars = @(Get-ApiGameJars -Directory (Join-Path $root 'api-game\build\libs'))
    $step1Ok = ($noGame.ExitCode -eq 0) -and $noGame.Output.Contains('BUILD SUCCESSFUL') -and $noGameSkipLine -and ($noGameJars.Count -eq 0)
    if ($step1Ok) {
        Write-StepResult -Step 'Step 1' -Status 'PASS' -Detail 'BUILD SUCCESSFUL, skip log present, no api-game jar produced'
    } else {
        $reason = @()
        if ($noGame.ExitCode -ne 0) { $reason += "exit=$($noGame.ExitCode)" }
        if (-not $noGame.Output.Contains('BUILD SUCCESSFUL')) { $reason += 'missing BUILD SUCCESSFUL' }
        if (-not $noGameSkipLine) { $reason += 'missing skip log line' }
        if ($noGameJars.Count -ne 0) { $reason += ('unexpected api-game jars: ' + ($noGameJars -join ', ')) }
        Write-StepResult -Step 'Step 1' -Status 'FAIL' -Detail ($reason -join '; ')
        Show-FailureOutput -Result $noGame
    }

    Write-Host ''
    Write-Host '== Step 2: with-game build'
    $classpathStatus = Get-GameClasspathStatus
    if (-not $classpathStatus.Present) {
        $script:skippedSteps.Add('2')
        Write-StepResult -Step 'Step 2' -Status 'SKIP' -Detail 'no game classpath present (WEAVER_GAME_DIR/classpath.txt or server jar fallback)'
    } else {
        $script:ranSteps.Add('2')
        Write-Host ("  classpath source: " + $classpathStatus.Source)
        $withGameArgs = @('clean', 'build', '--no-build-cache', '--rerun-tasks', '--no-daemon')
        $withGame = Invoke-CapturedCommand -Name 'step2-with-game' -CommandArguments $withGameArgs
        $agentLibJars = @(Get-ApiGameJars -Directory (Join-Path $root 'core\build\agent\lib'))
        $classPathEntries = Get-AgentClassPathEntries
        $manifestGameJars = @()
        $manifestBaseApiJars = @()
        if ($null -ne $classPathEntries) {
            $manifestGameJars = @($classPathEntries | Where-Object { $_ -match '^lib/api-game-.*\.jar$' })
            $manifestBaseApiJars = @($classPathEntries | Where-Object { $_ -match '^lib/api-(?!game-).*\.jar$' })
        }
        $step2Ok = ($withGame.ExitCode -eq 0) -and $withGame.Output.Contains('BUILD SUCCESSFUL') -and
            ($agentLibJars.Count -eq 1) -and ($manifestGameJars.Count -eq 1) -and ($manifestBaseApiJars.Count -eq 1)
        if ($step2Ok) {
            Write-StepResult -Step 'Step 2' -Status 'PASS' -Detail ("BUILD SUCCESSFUL, agent lib api-game jars=1 (" + $agentLibJars[0] + '), manifest Class-Path api-game entries=1, base api entries=1')
        } else {
            $reason = @()
            if ($withGame.ExitCode -ne 0) { $reason += "exit=$($withGame.ExitCode)" }
            if (-not $withGame.Output.Contains('BUILD SUCCESSFUL')) { $reason += 'missing BUILD SUCCESSFUL' }
            if ($agentLibJars.Count -ne 1) { $reason += "agent lib api-game jars=$($agentLibJars.Count)" }
            if ($manifestGameJars.Count -ne 1) { $reason += "manifest Class-Path api-game entries=$($manifestGameJars.Count)" }
            if ($manifestBaseApiJars.Count -ne 1) { $reason += "manifest Class-Path base api entries=$($manifestBaseApiJars.Count)" }
            Write-StepResult -Step 'Step 2' -Status 'FAIL' -Detail ($reason -join '; ')
            Show-FailureOutput -Result $withGame
        }
    }

    Write-Host ''
    Write-Host '== Step 3: requireGameApi failure path'
    $script:ranSteps.Add('3')
    $requireArgs = @(
        'build', '-Pweaver.forceNoGameApi=true', '-Pweaver.requireGameApi=true'
    )
    $require = Invoke-CapturedCommand -Name 'step3-require-failure' -CommandArguments $requireArgs
    $remediation = $require.Output.Contains('WEAVER_GAME_DIR') -and
        $require.Output.Contains('server-26.3.jar') -and
        $require.Output.Contains('-Pweaver.skipGameApi=true')
    $step3Ok = ($require.ExitCode -ne 0) -and $remediation -and (-not $require.Output.Contains('BUILD SUCCESSFUL'))
    if ($step3Ok) {
        Write-StepResult -Step 'Step 3' -Status 'PASS' -Detail 'build failed as expected and printed all three remediation options'
    } else {
        $reason = @()
        if ($require.ExitCode -eq 0) { $reason += 'build unexpectedly succeeded' }
        if (-not $remediation) { $reason += 'remediation text incomplete' }
        if ($require.Output.Contains('BUILD SUCCESSFUL')) { $reason += 'BUILD SUCCESSFUL present' }
        Write-StepResult -Step 'Step 3' -Status 'FAIL' -Detail ($reason -join '; ')
        Show-FailureOutput -Result $require
    }

    Write-Host ''
    Write-Host '== Step 4: skipGameApi build'
    $script:ranSteps.Add('4')
    $skipArgs = @('build', '-Pweaver.skipGameApi=true')
    $skip = Invoke-CapturedCommand -Name 'step4-skip' -CommandArguments $skipArgs
    $skipLine = $skip.Output.Contains('[weaver] api-game SKIPPED reason=skip-game-api')
    $skipAgentLibJars = @(Get-ApiGameJars -Directory (Join-Path $root 'core\build\agent\lib'))
    $skipClassPathEntries = Get-AgentClassPathEntries
    $skipManifestGameJars = @()
    $skipManifestBaseApiJars = @()
    if ($null -ne $skipClassPathEntries) {
        $skipManifestGameJars = @($skipClassPathEntries | Where-Object { $_ -match '^lib/api-game-.*\.jar$' })
        $skipManifestBaseApiJars = @($skipClassPathEntries | Where-Object { $_ -match '^lib/api-(?!game-).*\.jar$' })
    }
    $step4Ok = ($skip.ExitCode -eq 0) -and $skip.Output.Contains('BUILD SUCCESSFUL') -and $skipLine -and
        ($skipAgentLibJars.Count -eq 0) -and ($null -ne $skipClassPathEntries) -and
        ($skipManifestGameJars.Count -eq 0) -and ($skipManifestBaseApiJars.Count -eq 1)
    if ($step4Ok) {
        Write-StepResult -Step 'Step 4' -Status 'PASS' -Detail 'BUILD SUCCESSFUL, skip log present, no api-game jar or manifest entry remain, base api entry=1'
    } else {
        $reason = @()
        if ($skip.ExitCode -ne 0) { $reason += "exit=$($skip.ExitCode)" }
        if (-not $skip.Output.Contains('BUILD SUCCESSFUL')) { $reason += 'missing BUILD SUCCESSFUL' }
        if (-not $skipLine) { $reason += 'missing skip log line' }
        if ($skipAgentLibJars.Count -ne 0) { $reason += ('stale agent lib api-game jars: ' + ($skipAgentLibJars -join ', ')) }
        if ($null -eq $skipClassPathEntries) { $reason += 'agent manifest missing or unreadable' }
        if ($skipManifestGameJars.Count -ne 0) { $reason += ('stale manifest api-game entries: ' + ($skipManifestGameJars -join ', ')) }
        if ($skipManifestBaseApiJars.Count -ne 1) { $reason += "manifest Class-Path base api entries=$($skipManifestBaseApiJars.Count)" }
        Write-StepResult -Step 'Step 4' -Status 'FAIL' -Detail ($reason -join '; ')
        Show-FailureOutput -Result $skip
    }

    Write-Host ''
    Write-Host '== Step 5: Gradle plugin build'
    $script:ranSteps.Add('5')
    $pluginArgs = @('-p', 'tools\gradle-plugin', 'build')
    $plugin = Invoke-CapturedCommand -Name 'step5-plugin' -CommandArguments $pluginArgs
    $step5Ok = ($plugin.ExitCode -eq 0) -and $plugin.Output.Contains('BUILD SUCCESSFUL')
    if ($step5Ok) {
        Write-StepResult -Step 'Step 5' -Status 'PASS' -Detail 'tools/gradle-plugin BUILD SUCCESSFUL'
    } else {
        Write-StepResult -Step 'Step 5' -Status 'FAIL' -Detail "exit=$($plugin.ExitCode) or missing BUILD SUCCESSFUL"
        Show-FailureOutput -Result $plugin
    }

    Write-Host ''
    Write-Host '== Step 6: publishing POM gates'
    $script:ranSteps.Add('6')
    $pomArgs = @(
        ':api:verifyApiPom', ':api-game:verifyPomHasNoGameDependencies',
        '--no-daemon'
    )
    $pom = Invoke-CapturedCommand -Name 'step6-poms' -CommandArguments $pomArgs
    $pomMetadata = $pom.Output.Contains(':api:verifyApiPom') -and
        $pom.Output.Contains(':api-game:verifyPomHasNoGameDependencies')
    $step6Ok = ($pom.ExitCode -eq 0) -and $pom.Output.Contains('BUILD SUCCESSFUL') -and $pomMetadata
    if ($step6Ok) {
        Write-StepResult -Step 'Step 6' -Status 'PASS' -Detail 'api and api-game POM metadata gates passed with no game dependencies'
    } else {
        $reason = @()
        if ($pom.ExitCode -ne 0) { $reason += "exit=$($pom.ExitCode)" }
        if (-not $pom.Output.Contains('BUILD SUCCESSFUL')) { $reason += 'missing BUILD SUCCESSFUL' }
        if (-not $pomMetadata) { $reason += 'POM task output missing' }
        Write-StepResult -Step 'Step 6' -Status 'FAIL' -Detail ($reason -join '; ')
        Show-FailureOutput -Result $pom
    }

    Write-Host ''
    Write-Host '== Step 7: toolchain fixture and loader proof'
    $script:ranSteps.Add('7')
    $fixtureScript = Join-Path $root 'examples\toolchain-fixture\build.ps1'
    if (-not (Test-Path -LiteralPath $fixtureScript -PathType Leaf)) {
        Write-StepResult -Step 'Step 7' -Status 'FAIL' -Detail "fixture build script missing: $fixtureScript"
    } else {
        $fixture = Invoke-CapturedScript -Name 'step7-toolchain-fixture' -ScriptPath $fixtureScript
        $fixtureBuild = $fixture.Output.Contains('TOOLCHAINFIXTURE BUILD ok=true')
        $fixtureLoader = $fixture.Output.Contains('TOOLCHAINFIXTURE LOADER ok=true')
        $step7Ok = ($fixture.ExitCode -eq 0) -and $fixtureBuild -and $fixtureLoader
        if ($step7Ok) {
            Write-StepResult -Step 'Step 7' -Status 'PASS' -Detail 'fixture builds with Maven range + optional + JIJ and the loader proof passes'
        } else {
            $reason = @()
            if ($fixture.ExitCode -ne 0) { $reason += "exit=$($fixture.ExitCode)" }
            if (-not $fixtureBuild) { $reason += 'missing TOOLCHAINFIXTURE BUILD ok=true' }
            if (-not $fixtureLoader) { $reason += 'missing TOOLCHAINFIXTURE LOADER ok=true' }
            Write-StepResult -Step 'Step 7' -Status 'FAIL' -Detail ($reason -join '; ')
            Show-FailureOutput -Result $fixture
        }
    }

    Write-Host ''
    Write-Host '== Step 8: git diff --check'
    $script:ranSteps.Add('8')
    Push-Location -LiteralPath $root
    try {
        $previousErrorAction = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $checkLines = @(& git diff --check 2>&1 | ForEach-Object { $_.ToString() })
        $checkExit = $LASTEXITCODE
        $ErrorActionPreference = $previousErrorAction
    } finally {
        Pop-Location
    }
    $checkLog = Join-Path $captureDir 'step8-diff-check.log'
    Set-Content -LiteralPath $checkLog -Value ($checkLines -join [Environment]::NewLine) -Encoding UTF8
    $whitespaceErrors = @($checkLines | Where-Object {
        $_.Trim() -ne '' -and
        $_ -notmatch '^warning:' -and
        $_ -notmatch 'LF will be replaced by CRLF'
    })
    $step8Ok = ($whitespaceErrors.Count -eq 0)
    if ($step8Ok -and $checkExit -eq 0) {
        Write-StepResult -Step 'Step 8' -Status 'PASS' -Detail 'no whitespace errors (LF/CRLF warnings ignored)'
    } elseif ($step8Ok) {
        Write-StepResult -Step 'Step 8' -Status 'FAIL' -Detail "git diff --check exited $checkExit without identifiable whitespace errors"
        Write-Host ("  log: " + $checkLog)
    } else {
        Write-StepResult -Step 'Step 8' -Status 'FAIL' -Detail ('whitespace errors: ' + ($whitespaceErrors -join ' | '))
        Write-Host ("  log: " + $checkLog)
    }

    Write-Host ''
    Write-Host '== Step 9: restore with-game agent lib'
    $script:ranSteps.Add('9')
    $classpathPresent = (Test-Path -LiteralPath (Join-Path $root 'work\game-26.3\classpath.txt') -PathType Leaf) -or
        (Test-Path -LiteralPath (Join-Path $root 'work\server-26.3\versions\26.3\server-26.3.jar') -PathType Leaf)
    if (-not $classpathPresent) {
        $script:skippedSteps.Add('9')
        Write-StepResult -Step 'Step 9' -Status 'SKIP' -Detail 'no game classpath; proofs require a with-game build'
    } else {
        $restoreArgs = @(':core:agentJar')
        $restore = Invoke-CapturedCommand -Name 'step9-restore' -CommandArguments $restoreArgs
        $restoreJars = @(Get-ApiGameJars -Directory (Join-Path $root 'core\build\agent\lib'))
        $restoreEntries = Get-AgentClassPathEntries
        $restoreGameEntries = @()
        if ($null -ne $restoreEntries) {
            $restoreGameEntries = @($restoreEntries | Where-Object { $_ -match '^lib/api-game-.*\.jar$' })
        }
        $step9Ok = ($restore.ExitCode -eq 0) -and $restore.Output.Contains('BUILD SUCCESSFUL') -and
            ($restoreJars.Count -eq 1) -and ($restoreGameEntries.Count -eq 1)
        if ($step9Ok) {
            Write-StepResult -Step 'Step 9' -Status 'PASS' -Detail 'agent lib restored with exactly one api-game jar and manifest entry'
        } else {
            $reason = @()
            if ($restore.ExitCode -ne 0) { $reason += "exit=$($restore.ExitCode)" }
            if (-not $restore.Output.Contains('BUILD SUCCESSFUL')) { $reason += 'missing BUILD SUCCESSFUL' }
            if ($restoreJars.Count -ne 1) { $reason += "agent lib api-game jars=$($restoreJars.Count)" }
            if ($restoreGameEntries.Count -ne 1) { $reason += "manifest api-game entries=$($restoreGameEntries.Count)" }
            Write-StepResult -Step 'Step 9' -Status 'FAIL' -Detail ($reason -join '; ')
            Show-FailureOutput -Result $restore
        }
    }
    Write-Host ''
    Write-Host '== Step 10: patched game pipeline'
    $script:ranSteps.Add('10')
    $patcherVerify = Join-Path $root 'tools\patcher\verify.ps1'
    $patcherClientJar = Join-Path $root 'work\game-26.3\client.jar'
    $patcherServerJar = Join-Path $root 'work\server-26.3\versions\26.3\server-26.3.jar'
    if (-not (Test-Path -LiteralPath $patcherVerify -PathType Leaf) -or
        -not (Test-Path -LiteralPath $patcherClientJar -PathType Leaf) -or
        -not (Test-Path -LiteralPath $patcherServerJar -PathType Leaf)) {
        $script:skippedSteps.Add('10')
        Write-StepResult -Step 'Step 10' -Status 'SKIP' -Detail 'patcher or official game jars are missing'
    } else {
        $patcherResult = Invoke-CapturedScript -Name 'step10-patcher' -ScriptPath $patcherVerify
        $patcherOk = ($patcherResult.ExitCode -eq 0) -and
            $patcherResult.Output.Contains('PATCHER VERIFY ok=true') -and
            $patcherResult.Output.Contains('PATCHER CLASSLOAD') -and
            $patcherResult.Output.Contains('signatures=stripped') -and
            $patcherResult.Output.Contains('PATCHER VERIFY source=ok')
        if ($patcherOk) {
            Write-StepResult -Step 'Step 10' -Status 'PASS' -Detail 'binary and source patch pipelines: deterministic, signatures stripped, full class-load verification'
        } else {
            Write-StepResult -Step 'Step 10' -Status 'FAIL' -Detail "exit=$($patcherResult.ExitCode); missing patcher markers"
            Show-FailureOutput -Result $patcherResult
        }
    }
} catch {
    $script:failed = $true
    Write-Host ''
    Write-Host ("[FAIL] Unexpected error: " + $_.Exception.Message) -ForegroundColor Red
}

Write-Host ''
Write-Host ("steps ran: " + ($script:ranSteps -join ', '))
if ($script:skippedSteps.Count -gt 0) {
    Write-Host ("steps skipped: " + ($script:skippedSteps -join ', '))
}
$okText = 'false'
if (-not $script:failed) { $okText = 'true' }
Write-Host ("WEAVER VERIFY ok=$okText steps=10")

if ($script:failed) { exit 1 }
exit 0
