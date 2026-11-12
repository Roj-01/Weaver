#requires -Version 5.1
[CmdletBinding()]
param(
    [switch]$SkipGameProofs,
    [switch]$SkipClientProof,
    [switch]$SkipSharedProof,
    [switch]$SkipBenchmarks,
    [switch]$SkipBuildMatrix,
    [switch]$Quick
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $root 'gradlew.bat'
$powershellExe = Join-Path $PSHOME 'powershell.exe'
$captureDir = Join-Path ([System.IO.Path]::GetTempPath()) ("weaver-verify-ci-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $captureDir -Force | Out-Null

$BuildTimeout = 900
$UnitTestTimeout = 900
$StandaloneTimeout = 300
$ProofServerTimeout = 240
$ProofClientTimeout = 360
$SharedTimeout = 300
$BenchTimeout = 600

$script:results = New-Object System.Collections.Generic.List[object]
$script:failed = $false
$script:sharedMode = 'not-run'
$script:started = Get-Date

function Write-StepResult {
    param(
        [Parameter(Mandatory = $true)][string]$Step,
        [Parameter(Mandatory = $true)][string]$Status,
        [Parameter(Mandatory = $true)][string]$Detail,
        [string]$LogPath = ''
    )
    $color = 'Red'
    if ($Status -eq 'PASS') { $color = 'Green' }
    if ($Status -eq 'SKIP') { $color = 'Yellow' }
    Write-Host ("[{0}] {1}: {2}" -f $Status, $Step, $Detail) -ForegroundColor $color
    if ($LogPath -ne '') { Write-Host ("  log: " + $LogPath) }
    if ($Status -eq 'FAIL') { $script:failed = $true }
    $script:results.Add([pscustomobject]@{
        Step = $Step
        Status = $Status
        Detail = $Detail
        LogPath = $LogPath
    })
}

function Invoke-GateStep {
    param(
        [Parameter(Mandatory = $true)][string]$Step,
        [Parameter(Mandatory = $true)][scriptblock]$Action
    )
    try {
        & $Action
    } catch {
        Write-StepResult -Step $Step -Status 'FAIL' -Detail ('unexpected error: ' + $_.Exception.Message)
    }
}

function Quote-Argument {
    param([AllowEmptyString()][string]$Value)
    if ($null -eq $Value -or $Value.Length -eq 0) { return '""' }
    if ($Value -notmatch '[\s"]') { return $Value }
    return '"' + ($Value -replace '"', '\"') + '"'
}

function Stop-ProcessTree {
    param([Parameter(Mandatory = $true)][int]$ProcessId)
    try { & taskkill.exe /T /F /PID $ProcessId 2>&1 | Out-Null } catch { }
    try {
        $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
        if ($null -ne $process) { $process.Kill() }
    } catch { }
}

function Start-CapturedProcess {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$FileName,
        [string]$Arguments = '',
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )
    $logPath = Join-Path $captureDir ($Name + '.log')
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $FileName
    $startInfo.Arguments = $Arguments
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    $started = Get-Date
    try {
        [void]$process.Start()
    } catch {
        $message = $_.Exception.Message
        [System.IO.File]::WriteAllText($logPath, $message)
        return [pscustomobject]@{
            ExitCode = -1
            TimedOut = $false
            Output = $message
            LogPath = $logPath
            DurationSeconds = 0
        }
    }

    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $finished = $process.WaitForExit($TimeoutSeconds * 1000)
    $timedOut = $false
    if (-not $finished) {
        $timedOut = $true
        Stop-ProcessTree -ProcessId $process.Id
        try { [void]$process.WaitForExit(10000) } catch { }
    } else {
        $process.WaitForExit()
    }

    $stdout = ''
    $stderr = ''
    try { $stdout = $stdoutTask.GetAwaiter().GetResult() } catch { $stdout = '' }
    try { $stderr = $stderrTask.GetAwaiter().GetResult() } catch { $stderr = '' }
    $output = $stdout
    if ($stderr.Length -gt 0) {
        $output = $output + [Environment]::NewLine + $stderr
    }
    [System.IO.File]::WriteAllText($logPath, $output)

    $exitCode = -1
    try { $exitCode = $process.ExitCode } catch { }
    $duration = [math]::Round(((Get-Date) - $started).TotalSeconds, 1)
    return [pscustomobject]@{
        ExitCode = $exitCode
        TimedOut = $timedOut
        Output = $output
        LogPath = $logPath
        DurationSeconds = $duration
    }
}

function Invoke-Gradle {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string[]]$GradleArguments,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )
    $quotedArguments = @($GradleArguments | ForEach-Object { Quote-Argument $_ })
    $commandLine = (Quote-Argument $gradlew) + ' ' + ($quotedArguments -join ' ')
    $cmdArguments = '/d /s /c "' + $commandLine + '"'
    return Start-CapturedProcess -Name $Name -FileName $env:ComSpec -Arguments $cmdArguments `
        -WorkingDirectory $root -TimeoutSeconds $TimeoutSeconds
}

function Invoke-RepoScreenScript {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$ScriptPath,
        [string[]]$ScriptArguments = @(),
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )
    $parts = New-Object System.Collections.Generic.List[string]
    $parts.Add('-NoProfile')
    $parts.Add('-ExecutionPolicy')
    $parts.Add('Bypass')
    $parts.Add('-File')
    $parts.Add((Quote-Argument $ScriptPath))
    foreach ($argument in $ScriptArguments) {
        $parts.Add((Quote-Argument $argument))
    }
    return Start-CapturedProcess -Name $Name -FileName $powershellExe `
        -Arguments ($parts -join ' ') -WorkingDirectory $root -TimeoutSeconds $TimeoutSeconds
}

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$FileName,
        [string[]]$NativeArguments = @(),
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )
    $quotedArguments = @($NativeArguments | ForEach-Object { Quote-Argument $_ })
    return Start-CapturedProcess -Name $Name -FileName $FileName `
        -Arguments ($quotedArguments -join ' ') -WorkingDirectory $root `
        -TimeoutSeconds $TimeoutSeconds
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
        Where-Object {
            $_.Name -like 'api-game-*.jar' -and
            $_.Name -notlike '*-sources.jar' -and
            $_.Name -notlike '*-javadoc.jar'
        } |
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

function Test-GameProofPrerequisites {
    $classpath = Get-GameClasspathStatus
    if (-not $classpath.Present) {
        return [pscustomobject]@{ Ok = $false; Reason = 'no game classpath (WEAVER_GAME_DIR/classpath.txt or server jar)' }
    }
    $agentJar = Join-Path $root 'core\build\agent\weaver-agent.jar'
    if (-not (Test-Path -LiteralPath $agentJar -PathType Leaf)) {
        return [pscustomobject]@{ Ok = $false; Reason = 'agent jar missing; run :core:agentJar' }
    }
    $agentLib = Join-Path $root 'core\build\agent\lib'
    if (-not (Test-Path -LiteralPath $agentLib -PathType Container)) {
        return [pscustomobject]@{ Ok = $false; Reason = 'agent lib missing; run :core:agentJar' }
    }
    if (@(Get-ApiGameJars -Directory $agentLib).Count -eq 0) {
        return [pscustomobject]@{ Ok = $false; Reason = 'agent lib has no api-game jar; restore the with-game build' }
    }
    $serverJar = Join-Path $root 'work\server-26.3\versions\26.3\server-26.3.jar'
    if (-not (Test-Path -LiteralPath $serverJar -PathType Leaf)) {
        return [pscustomobject]@{ Ok = $false; Reason = 'dedicated server jar missing: work/server-26.3' }
    }
    return [pscustomobject]@{ Ok = $true; Reason = '' }
}

function Get-TestResultSummary {
    param([Parameter(Mandatory = $true)][string]$Module)
    $directory = Join-Path $root ($Module + '\build\test-results\test')
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        return [pscustomobject]@{ Total = 0; Failures = 0; Errors = 0; Skipped = 0 }
    }
    $total = 0
    $failures = 0
    $errors = 0
    $skipped = 0
    foreach ($file in Get-ChildItem -LiteralPath $directory -Filter '*.xml' -File) {
        try {
            [xml]$document = Get-Content -LiteralPath $file.FullName -Raw
            if ($null -ne $document.testsuite) {
                $total += [int]$document.testsuite.tests
                $failures += [int]$document.testsuite.failures
                $errors += [int]$document.testsuite.errors
                $skipped += [int]$document.testsuite.skipped
            }
        } catch {
        }
    }
    return [pscustomobject]@{
        Total = $total
        Failures = $failures
        Errors = $errors
        Skipped = $skipped
    }
}

function Format-TestSummary {
    param([pscustomobject]$Summary)
    return ("tests={0} failures={1} errors={2} skipped={3}" -f $Summary.Total,
        $Summary.Failures, $Summary.Errors, $Summary.Skipped)
}

Write-Host "Weaver verify-ci (root: $root)"
Write-Host ("Captured logs: " + $captureDir)
Write-Host ("Flags: SkipBuildMatrix={0} SkipGameProofs={1} SkipClientProof={2} SkipSharedProof={3} SkipBenchmarks={4} Quick={5}" -f `
    [bool]$SkipBuildMatrix, [bool]$SkipGameProofs, [bool]$SkipClientProof, `
    [bool]$SkipSharedProof, [bool]$SkipBenchmarks, [bool]$Quick)
if ($Quick) {
    Write-Host 'Quick mode implies SkipGameProofs, SkipSharedProof, and SkipBenchmarks.'
}

Invoke-GateStep -Step 'A-with-game-build' -Action {
    if ($SkipBuildMatrix) {
        Write-StepResult -Step 'A-with-game-build' -Status 'SKIP' -Detail 'skipped by -SkipBuildMatrix'
        return
    }
    $classpath = Get-GameClasspathStatus
    if (-not $classpath.Present) {
        Write-StepResult -Step 'A-with-game-build' -Status 'SKIP' -Detail 'no game classpath; cannot prove the with-game build'
        return
    }
    $result = Invoke-Gradle -Name 'A-with-game-build' -GradleArguments @(
        'clean', 'build', '--no-build-cache', '--rerun-tasks', '--no-daemon'
    ) -TimeoutSeconds $BuildTimeout
    $jars = @(Get-ApiGameJars -Directory (Join-Path $root 'api-game\build\libs'))
    $ok = ($result.ExitCode -eq 0) -and (-not $result.TimedOut) -and
        $result.Output.Contains('BUILD SUCCESSFUL') -and
        (-not $result.Output.Contains('[weaver] api-game SKIPPED')) -and
        ($jars.Count -ge 1)
    if ($ok) {
        Write-StepResult -Step 'A-with-game-build' -Status 'PASS' `
            -Detail ("BUILD SUCCESSFUL, classpath={0}, api-game jars={1}" -f $classpath.Source, $jars.Count) `
            -LogPath $result.LogPath
    } else {
        $reasons = @()
        if ($result.TimedOut) { $reasons += "timeout=${BuildTimeout}s" }
        if ($result.ExitCode -ne 0) { $reasons += "exit=$($result.ExitCode)" }
        if (-not $result.Output.Contains('BUILD SUCCESSFUL')) { $reasons += 'missing BUILD SUCCESSFUL' }
        if ($result.Output.Contains('[weaver] api-game SKIPPED')) { $reasons += 'api-game was skipped' }
        if ($jars.Count -lt 1) { $reasons += 'missing api-game jar' }
        Write-StepResult -Step 'A-with-game-build' -Status 'FAIL' -Detail ($reasons -join '; ') `
            -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    }
}

Invoke-GateStep -Step 'B-no-game-build' -Action {
    if ($SkipBuildMatrix) {
        Write-StepResult -Step 'B-no-game-build' -Status 'SKIP' -Detail 'skipped by -SkipBuildMatrix'
        return
    }
    $result = Invoke-Gradle -Name 'B-no-game-build' -GradleArguments @(
        'clean', 'build', '--no-build-cache', '--rerun-tasks', '--no-daemon',
        '-Pweaver.forceNoGameApi=true'
    ) -TimeoutSeconds $BuildTimeout
    $skipLine = $result.Output.Contains('[weaver] api-game SKIPPED reason=no-game-classpath')
    $libsJars = @(Get-ApiGameJars -Directory (Join-Path $root 'api-game\build\libs'))
    $agentJars = @(Get-ApiGameJars -Directory (Join-Path $root 'core\build\agent\lib'))
    $ok = ($result.ExitCode -eq 0) -and (-not $result.TimedOut) -and
        $result.Output.Contains('BUILD SUCCESSFUL') -and $skipLine -and
        ($libsJars.Count -eq 0) -and ($agentJars.Count -eq 0)
    if ($ok) {
        Write-StepResult -Step 'B-no-game-build' -Status 'PASS' `
            -Detail 'BUILD SUCCESSFUL, skip line present, no api-game jar in libs or agent lib' `
            -LogPath $result.LogPath
    } else {
        $reasons = @()
        if ($result.TimedOut) { $reasons += "timeout=${BuildTimeout}s" }
        if ($result.ExitCode -ne 0) { $reasons += "exit=$($result.ExitCode)" }
        if (-not $result.Output.Contains('BUILD SUCCESSFUL')) { $reasons += 'missing BUILD SUCCESSFUL' }
        if (-not $skipLine) { $reasons += 'missing no-game skip line' }
        if ($libsJars.Count -ne 0) { $reasons += ('api-game jars in libs: ' + ($libsJars -join ', ')) }
        if ($agentJars.Count -ne 0) { $reasons += ('api-game jars in agent lib: ' + ($agentJars -join ', ')) }
        Write-StepResult -Step 'B-no-game-build' -Status 'FAIL' -Detail ($reasons -join '; ') `
            -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    }
}

Invoke-GateStep -Step 'C-require-game-failure' -Action {
    if ($SkipBuildMatrix) {
        Write-StepResult -Step 'C-require-game-failure' -Status 'SKIP' -Detail 'skipped by -SkipBuildMatrix'
        return
    }
    $result = Invoke-Gradle -Name 'C-require-game-failure' -GradleArguments @(
        'build', '-Pweaver.forceNoGameApi=true', '-Pweaver.requireGameApi=true'
    ) -TimeoutSeconds $BuildTimeout
    $remediation = $result.Output.Contains('WEAVER_GAME_DIR') -and
        $result.Output.Contains('server-26.3.jar') -and
        $result.Output.Contains('-Pweaver.skipGameApi=true')
    $ok = ($result.ExitCode -ne 0) -and (-not $result.TimedOut) -and $remediation -and
        (-not $result.Output.Contains('BUILD SUCCESSFUL'))
    if ($ok) {
        Write-StepResult -Step 'C-require-game-failure' -Status 'PASS' `
            -Detail 'build failed as expected and printed all three remediation options' `
            -LogPath $result.LogPath
    } else {
        $reasons = @()
        if ($result.TimedOut) { $reasons += "timeout=${BuildTimeout}s" }
        if ($result.ExitCode -eq 0) { $reasons += 'build unexpectedly succeeded' }
        if (-not $remediation) { $reasons += 'remediation text incomplete' }
        if ($result.Output.Contains('BUILD SUCCESSFUL')) { $reasons += 'BUILD SUCCESSFUL present' }
        Write-StepResult -Step 'C-require-game-failure' -Status 'FAIL' -Detail ($reasons -join '; ') `
            -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    }
}

Invoke-GateStep -Step 'D-skip-game-api' -Action {
    if ($SkipBuildMatrix) {
        Write-StepResult -Step 'D-skip-game-api' -Status 'SKIP' -Detail 'skipped by -SkipBuildMatrix'
        return
    }
    $result = Invoke-Gradle -Name 'D-skip-game-api' -GradleArguments @(
        'build', '-Pweaver.skipGameApi=true'
    ) -TimeoutSeconds $BuildTimeout
    $skipLine = $result.Output.Contains('[weaver] api-game SKIPPED reason=skip-game-api')
    $agentJars = @(Get-ApiGameJars -Directory (Join-Path $root 'core\build\agent\lib'))
    $entries = Get-AgentClassPathEntries
    $gameEntries = @()
    $baseEntries = @()
    if ($null -ne $entries) {
        $gameEntries = @($entries | Where-Object { $_ -match '^lib/api-game-.*\.jar$' })
        $baseEntries = @($entries | Where-Object { $_ -match '^lib/api-(?!game-).*\.jar$' })
    }
    $ok = ($result.ExitCode -eq 0) -and (-not $result.TimedOut) -and
        $result.Output.Contains('BUILD SUCCESSFUL') -and $skipLine -and
        ($agentJars.Count -eq 0) -and ($null -ne $entries) -and
        ($gameEntries.Count -eq 0) -and ($baseEntries.Count -eq 1)
    if ($ok) {
        Write-StepResult -Step 'D-skip-game-api' -Status 'PASS' `
            -Detail 'BUILD SUCCESSFUL, skip line present, no api-game jar or manifest entry, base api entry=1' `
            -LogPath $result.LogPath
    } else {
        $reasons = @()
        if ($result.TimedOut) { $reasons += "timeout=${BuildTimeout}s" }
        if ($result.ExitCode -ne 0) { $reasons += "exit=$($result.ExitCode)" }
        if (-not $result.Output.Contains('BUILD SUCCESSFUL')) { $reasons += 'missing BUILD SUCCESSFUL' }
        if (-not $skipLine) { $reasons += 'missing skip-game-api line' }
        if ($agentJars.Count -ne 0) { $reasons += ('stale agent lib jars: ' + ($agentJars -join ', ')) }
        if ($null -eq $entries) { $reasons += 'agent manifest missing or unreadable' }
        if ($gameEntries.Count -ne 0) { $reasons += ('stale manifest api-game entries: ' + ($gameEntries -join ', ')) }
        if ($baseEntries.Count -ne 1) { $reasons += "manifest base api entries=$($baseEntries.Count)" }
        Write-StepResult -Step 'D-skip-game-api' -Status 'FAIL' -Detail ($reasons -join '; ') `
            -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    }
}

Invoke-GateStep -Step 'E-restore-with-game' -Action {
    if ($SkipBuildMatrix) {
        Write-StepResult -Step 'E-restore-with-game' -Status 'SKIP' -Detail 'skipped by -SkipBuildMatrix'
        return
    }
    $classpath = Get-GameClasspathStatus
    if (-not $classpath.Present) {
        Write-StepResult -Step 'E-restore-with-game' -Status 'SKIP' -Detail 'no game classpath; nothing to restore'
        return
    }
    $result = Invoke-Gradle -Name 'E-restore-with-game' -GradleArguments @(
        ':core:agentJar'
    ) -TimeoutSeconds $BuildTimeout
    $agentJars = @(Get-ApiGameJars -Directory (Join-Path $root 'core\build\agent\lib'))
    $entries = Get-AgentClassPathEntries
    $gameEntries = @()
    if ($null -ne $entries) {
        $gameEntries = @($entries | Where-Object { $_ -match '^lib/api-game-.*\.jar$' })
    }
    $ok = ($result.ExitCode -eq 0) -and (-not $result.TimedOut) -and
        $result.Output.Contains('BUILD SUCCESSFUL') -and
        ($agentJars.Count -eq 1) -and ($gameEntries.Count -eq 1)
    if ($ok) {
        Write-StepResult -Step 'E-restore-with-game' -Status 'PASS' `
            -Detail 'agent lib restored with exactly one api-game jar and manifest entry' `
            -LogPath $result.LogPath
    } else {
        $reasons = @()
        if ($result.TimedOut) { $reasons += "timeout=${BuildTimeout}s" }
        if ($result.ExitCode -ne 0) { $reasons += "exit=$($result.ExitCode)" }
        if (-not $result.Output.Contains('BUILD SUCCESSFUL')) { $reasons += 'missing BUILD SUCCESSFUL' }
        if ($agentJars.Count -ne 1) { $reasons += "agent lib api-game jars=$($agentJars.Count)" }
        if ($gameEntries.Count -ne 1) { $reasons += "manifest api-game entries=$($gameEntries.Count)" }
        Write-StepResult -Step 'E-restore-with-game' -Status 'FAIL' -Detail ($reasons -join '; ') `
            -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    }
}

Invoke-GateStep -Step 'F-unit-tests' -Action {
    $classpath = Get-GameClasspathStatus
    if (-not $classpath.Present) {
        Write-StepResult -Step 'F-unit-tests' -Status 'SKIP' -Detail 'no game classpath; api-game tests require with-game mode'
        return
    }
    $result = Invoke-Gradle -Name 'F-unit-tests' -GradleArguments @(
        ':api:test', ':core:test', ':api-game:test', '--rerun-tasks', '--no-daemon'
    ) -TimeoutSeconds $UnitTestTimeout
    $apiSummary = Get-TestResultSummary -Module 'api'
    $coreSummary = Get-TestResultSummary -Module 'core'
    $gameSummary = Get-TestResultSummary -Module 'api-game'
    $total = $apiSummary.Total + $coreSummary.Total + $gameSummary.Total
    $failures = $apiSummary.Failures + $coreSummary.Failures + $gameSummary.Failures
    $errors = $apiSummary.Errors + $coreSummary.Errors + $gameSummary.Errors
    $detail = ("api [{0}], core [{1}], api-game [{2}], total tests={3} failures={4} errors={5}" -f `
        (Format-TestSummary $apiSummary), (Format-TestSummary $coreSummary),
        (Format-TestSummary $gameSummary), $total, $failures, $errors)
    $ok = ($result.ExitCode -eq 0) -and (-not $result.TimedOut) -and
        $result.Output.Contains('BUILD SUCCESSFUL') -and $failures -eq 0 -and $errors -eq 0
    if ($ok) {
        Write-StepResult -Step 'F-unit-tests' -Status 'PASS' -Detail $detail -LogPath $result.LogPath
    } else {
        $reasons = @()
        if ($result.TimedOut) { $reasons += "timeout=${UnitTestTimeout}s" }
        if ($result.ExitCode -ne 0) { $reasons += "exit=$($result.ExitCode)" }
        if (-not $result.Output.Contains('BUILD SUCCESSFUL')) { $reasons += 'missing BUILD SUCCESSFUL' }
        if ($failures -ne 0 -or $errors -ne 0) { $reasons += ("test failures=$failures errors=$errors") }
        Write-StepResult -Step 'F-unit-tests' -Status 'FAIL' `
            -Detail (($reasons -join '; ') + '; ' + $detail) -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    }
}

Invoke-GateStep -Step 'G-config-proof' -Action {
    $scriptPath = Join-Path $root 'examples\config-proof\run.ps1'
    $result = Invoke-RepoScreenScript -Name 'G-config-proof' -ScriptPath $scriptPath `
        -TimeoutSeconds $StandaloneTimeout
    $marker = 'CONFIG PROOF RESULT ok=true checks=58'
    $ok = ($result.ExitCode -eq 0) -and (-not $result.TimedOut) -and $result.Output.Contains($marker)
    if ($ok) {
        Write-StepResult -Step 'G-config-proof' -Status 'PASS' -Detail $marker -LogPath $result.LogPath
    } else {
        $reason = 'missing marker ' + $marker
        if ($result.TimedOut) { $reason = "timeout=${StandaloneTimeout}s" }
        elseif ($result.ExitCode -ne 0) { $reason = "exit=$($result.ExitCode); " + $reason }
        Write-StepResult -Step 'G-config-proof' -Status 'FAIL' -Detail $reason -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    }
}

Invoke-GateStep -Step 'G-network-proof' -Action {
    $scriptPath = Join-Path $root 'examples\network-proof\run.ps1'
    $result = Invoke-RepoScreenScript -Name 'G-network-proof' -ScriptPath $scriptPath `
        -TimeoutSeconds $StandaloneTimeout
    $marker = 'NETWORK PROOF RUN ok=true server=ok client=ok mismatch=ok'
    $ok = ($result.ExitCode -eq 0) -and (-not $result.TimedOut) -and $result.Output.Contains($marker)
    if ($ok) {
        Write-StepResult -Step 'G-network-proof' -Status 'PASS' -Detail $marker -LogPath $result.LogPath
    } else {
        $reason = 'missing marker ' + $marker
        if ($result.TimedOut) { $reason = "timeout=${StandaloneTimeout}s" }
        elseif ($result.ExitCode -ne 0) { $reason = "exit=$($result.ExitCode); " + $reason }
        Write-StepResult -Step 'G-network-proof' -Status 'FAIL' -Detail $reason -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    }
}

function Get-WeaverHookMisses {
    param([string]$LogPath)
    if (-not (Test-Path -LiteralPath $LogPath)) { return @() }
    return @(Select-String -LiteralPath $LogPath -Pattern 'MIXIN ANCHOR ABSENT mod=weaver-|MIXIN ENGINE ERROR .*weaver/weaver-' |
        ForEach-Object { $_.Line.Trim() } | Select-Object -First 5)
}

Invoke-GateStep -Step 'H-proof-server' -Action {
    if ($SkipGameProofs -or $Quick) {
        $flag = '-Quick'
        if ($SkipGameProofs) { $flag = '-SkipGameProofs' }
        Write-StepResult -Step 'H-proof-server' -Status 'SKIP' -Detail ("skipped by " + $flag)
        return
    }
    $prerequisites = Test-GameProofPrerequisites
    if (-not $prerequisites.Ok) {
        Write-StepResult -Step 'H-proof-server' -Status 'SKIP' -Detail $prerequisites.Reason
        return
    }
    $build = Invoke-RepoScreenScript -Name 'H-proof-server-build' `
        -ScriptPath (Join-Path $root 'examples\proof\build.ps1') -TimeoutSeconds $BuildTimeout
    if (($build.ExitCode -ne 0) -or $build.TimedOut -or
        (-not $build.Output.Contains('PROOF BUILD ok=true'))) {
        Write-StepResult -Step 'H-proof-server' -Status 'FAIL' `
            -Detail 'examples/proof/build.ps1 failed or did not print PROOF BUILD ok=true' `
            -LogPath $build.LogPath
        Show-FailureOutput -Result $build
        return
    }
    $result = Invoke-RepoScreenScript -Name 'H-proof-server' `
        -ScriptPath (Join-Path $root 'examples\proof\run-server.ps1') `
        -TimeoutSeconds $ProofServerTimeout
    $marker = 'PROOF RESULT ok=true side=server checks=20'
    $eventsMarker = 'GAME EVENTS PROOF RESULT ok=true'
    $ok = ($result.ExitCode -eq 0) -and (-not $result.TimedOut) -and
        $result.Output.Contains($marker) -and $result.Output.Contains('SERVER RESULT ok=true') -and
        $result.Output.Contains($eventsMarker)
    $hookMisses = @(Get-WeaverHookMisses -LogPath (Join-Path $root 'examples\proof\out\logs\server.log'))
    if ($hookMisses.Count -gt 0) { $ok = $false }
    if ($ok) {
        $eventsLine = @($result.Output -split "`r?`n" | Where-Object { $_ -match 'GAME EVENTS PROOF RESULT' }) | Select-Object -First 1
        Write-StepResult -Step 'H-proof-server' -Status 'PASS' -Detail ($marker + '; ' + $eventsLine.Trim()) -LogPath $result.LogPath
    } else {
        $reason = 'missing marker ' + $marker
        if (-not $result.Output.Contains($eventsMarker)) { $reason = 'missing marker ' + $eventsMarker }
        if ($hookMisses.Count -gt 0) { $reason = 'Weaver hook not bound: ' + ($hookMisses -join ' | ') }
        if ($result.TimedOut) { $reason = "timeout=${ProofServerTimeout}s" }
        elseif ($result.ExitCode -ne 0) { $reason = "exit=$($result.ExitCode); " + $reason }
        Write-StepResult -Step 'H-proof-server' -Status 'FAIL' -Detail $reason -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    }
}

Invoke-GateStep -Step 'H-gametest' -Action {
    if ($SkipGameProofs -or $Quick) {
        $flag = '-Quick'
        if ($SkipGameProofs) { $flag = '-SkipGameProofs' }
        Write-StepResult -Step 'H-gametest' -Status 'SKIP' -Detail ("skipped by " + $flag)
        return
    }
    $prerequisites = Test-GameProofPrerequisites
    if (-not $prerequisites.Ok) {
        Write-StepResult -Step 'H-gametest' -Status 'SKIP' -Detail $prerequisites.Reason
        return
    }
    $build = Invoke-RepoScreenScript -Name 'H-gametest-build' `
        -ScriptPath (Join-Path $root 'examples\gametest\build.ps1') -TimeoutSeconds $BuildTimeout
    if (($build.ExitCode -ne 0) -or $build.TimedOut -or
        (-not $build.Output.Contains('GAMETEST BUILD ok=true'))) {
        Write-StepResult -Step 'H-gametest' -Status 'FAIL' `
            -Detail 'examples/gametest/build.ps1 failed or did not print GAMETEST BUILD ok=true' `
            -LogPath $build.LogPath
        Show-FailureOutput -Result $build
        return
    }
    $runner = Join-Path $root 'examples\gametest\run.ps1'
    $pass = Invoke-RepoScreenScript -Name 'H-gametest' -ScriptPath $runner -TimeoutSeconds $ProofServerTimeout
    $expected = @(
        'GAMETEST CASE name=weavergametest:place_block result=passed',
        'GAMETEST CASE name=weavergametest:chest_item_handler result=passed',
        'GAMETEST CASE name=weavergametest:waits_ticks result=passed',
        'GAMETEST CASE name=weavergametest:expected_failure result=optional-failed',
        'GAMETEST RESULT ok=true exit=0')
    $missing = @($expected | Where-Object { -not $pass.Output.Contains($_) })
    if (($pass.ExitCode -ne 0) -or $pass.TimedOut -or $missing.Count -gt 0) {
        $reason = "exit=$($pass.ExitCode)"
        if ($missing.Count -gt 0) { $reason = 'missing ' + $missing[0] }
        if ($pass.TimedOut) { $reason = "timeout=${ProofServerTimeout}s" }
        Write-StepResult -Step 'H-gametest' -Status 'FAIL' -Detail $reason -LogPath $pass.LogPath
        Show-FailureOutput -Result $pass
        return
    }
    # A required failure must fail the run.
    $env:WEAVER_GAMETEST_FORCE_FAILURE = '1'
    try {
        $fail = Invoke-RepoScreenScript -Name 'H-gametest-forced-failure' -ScriptPath $runner `
            -TimeoutSeconds $ProofServerTimeout
    } finally {
        Remove-Item Env:WEAVER_GAMETEST_FORCE_FAILURE -ErrorAction SilentlyContinue
    }
    $failedAsExpected = ($fail.ExitCode -ne 0) -and (-not $fail.TimedOut) -and
        $fail.Output.Contains('GAMETEST CASE name=weavergametest:forced_failure result=failed') -and
        $fail.Output.Contains('GAMETEST RESULT ok=false exit=1')
    if (-not $failedAsExpected) {
        Write-StepResult -Step 'H-gametest' -Status 'FAIL' -Detail 'a failing required game test did not fail the run' `
            -LogPath $fail.LogPath
        Show-FailureOutput -Result $fail
        return
    }
    $line = @($pass.Output -split "`r?`n" | Where-Object { $_ -match 'GAMETEST RESULT' }) | Select-Object -First 1
    Write-StepResult -Step 'H-gametest' -Status 'PASS' -Detail ($line.Trim() + '; required failure fails the run') -LogPath $pass.LogPath
}

Invoke-GateStep -Step 'H-multiplayer' -Action {
    if ($SkipGameProofs -or $Quick) {
        $flag = '-Quick'
        if ($SkipGameProofs) { $flag = '-SkipGameProofs' }
        Write-StepResult -Step 'H-multiplayer' -Status 'SKIP' -Detail ("skipped by " + $flag)
        return
    }
    $prerequisites = Test-GameProofPrerequisites
    if (-not $prerequisites.Ok) {
        Write-StepResult -Step 'H-multiplayer' -Status 'SKIP' -Detail $prerequisites.Reason
        return
    }
    $build = Invoke-RepoScreenScript -Name 'H-multiplayer-build' `
        -ScriptPath (Join-Path $root 'examples\multiplayer\build.ps1') -TimeoutSeconds $BuildTimeout
    if (($build.ExitCode -ne 0) -or $build.TimedOut -or (-not $build.Output.Contains('MP BUILD ok=true'))) {
        Write-StepResult -Step 'H-multiplayer' -Status 'FAIL' `
            -Detail 'examples/multiplayer/build.ps1 failed or did not print MP BUILD ok=true' -LogPath $build.LogPath
        Show-FailureOutput -Result $build
        return
    }
    # A dedicated server and separate clients: one that matches and three that must be refused.
    $run = Invoke-RepoScreenScript -Name 'H-multiplayer' `
        -ScriptPath (Join-Path $root 'examples\multiplayer\run.ps1') -TimeoutSeconds 1500
    $marker = 'MP RESULT ok=true match=true extra-item=true version=true missing=true'
    if (($run.ExitCode -eq 0) -and (-not $run.TimedOut) -and $run.Output.Contains($marker)) {
        Write-StepResult -Step 'H-multiplayer' -Status 'PASS' -Detail $marker -LogPath $run.LogPath
    } else {
        $line = @($run.Output -split "`r?`n" | Where-Object { $_ -match 'MP RESULT' }) | Select-Object -First 1
        $reason = if ($run.TimedOut) { 'timeout=1500s' } elseif ($line) { $line.Trim() } else { "exit=$($run.ExitCode)" }
        Write-StepResult -Step 'H-multiplayer' -Status 'FAIL' -Detail $reason -LogPath $run.LogPath
        Show-FailureOutput -Result $run
    }
}

Invoke-GateStep -Step 'H-compat-suite' -Action {
    if ($SkipGameProofs -or $Quick) {
        $flag = '-Quick'
        if ($SkipGameProofs) { $flag = '-SkipGameProofs' }
        Write-StepResult -Step 'H-compat-suite' -Status 'SKIP' -Detail ("skipped by " + $flag)
        return
    }
    $prerequisites = Test-GameProofPrerequisites
    if (-not $prerequisites.Ok) {
        Write-StepResult -Step 'H-compat-suite' -Status 'SKIP' -Detail $prerequisites.Reason
        return
    }
    $build = Invoke-RepoScreenScript -Name 'H-compat-suite-build' `
        -ScriptPath (Join-Path $root 'examples\compat-suite\build.ps1') -TimeoutSeconds $BuildTimeout
    if (($build.ExitCode -ne 0) -or $build.TimedOut -or
        (-not $build.Output.Contains('COMPAT SUITE BUILD ok=true'))) {
        Write-StepResult -Step 'H-compat-suite' -Status 'FAIL' `
            -Detail 'examples/compat-suite/build.ps1 failed or did not print COMPAT SUITE BUILD ok=true' `
            -LogPath $build.LogPath
        Show-FailureOutput -Result $build
        return
    }
    $result = Invoke-RepoScreenScript -Name 'H-compat-suite' `
        -ScriptPath (Join-Path $root 'examples\compat-suite\run.ps1') `
        -TimeoutSeconds $ProofServerTimeout
    $marker = 'COMPAT SUITE RESULT ok=true side=server checks=20'
    $ok = ($result.ExitCode -eq 0) -and (-not $result.TimedOut) -and
        $result.Output.Contains($marker) -and $result.Output.Contains('COMPAT SUITE PROOF ok=true')
    if ($ok) {
        Write-StepResult -Step 'H-compat-suite' -Status 'PASS' -Detail $marker -LogPath $result.LogPath
    } else {
        $reason = 'missing marker ' + $marker
        if ($result.TimedOut) { $reason = "timeout=${ProofServerTimeout}s" }
        elseif ($result.ExitCode -ne 0) { $reason = "exit=$($result.ExitCode); " + $reason }
        Write-StepResult -Step 'H-compat-suite' -Status 'FAIL' -Detail $reason -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    }
}

Invoke-GateStep -Step 'H-client-proof' -Action {
    if ($SkipGameProofs -or $Quick) {
        $flag = '-Quick'
        if ($SkipGameProofs) { $flag = '-SkipGameProofs' }
        Write-StepResult -Step 'H-client-proof' -Status 'SKIP' -Detail ("skipped by " + $flag)
        return
    }
    if ($SkipClientProof) {
        Write-StepResult -Step 'H-client-proof' -Status 'SKIP' -Detail 'skipped by -SkipClientProof'
        return
    }
    $prerequisites = Test-GameProofPrerequisites
    if (-not $prerequisites.Ok) {
        Write-StepResult -Step 'H-client-proof' -Status 'SKIP' -Detail $prerequisites.Reason
        return
    }
    $fixture = Join-Path $root 'examples\proof\fixture-world\level.dat'
    if (-not (Test-Path -LiteralPath $fixture -PathType Leaf)) {
        Write-StepResult -Step 'H-client-proof' -Status 'SKIP' `
            -Detail 'client fixture world missing: examples/proof/fixture-world/level.dat'
        return
    }
    $build = Invoke-RepoScreenScript -Name 'H-client-proof-build' `
        -ScriptPath (Join-Path $root 'examples\client-proof\build.ps1') -TimeoutSeconds $BuildTimeout
    if (($build.ExitCode -ne 0) -or $build.TimedOut -or
        (-not $build.Output.Contains('CLIENT PROOF BUILD ok=true'))) {
        Write-StepResult -Step 'H-client-proof' -Status 'FAIL' `
            -Detail 'examples/client-proof/build.ps1 failed or did not print CLIENT PROOF BUILD ok=true' `
            -LogPath $build.LogPath
        Show-FailureOutput -Result $build
        return
    }
    $markers = @('CLIENT PROOF RESULT ok=true side=client', 'CLIENT PROOF CLIENT FIXTURE world=')
    $result = Invoke-RepoScreenScript -Name 'H-client-proof' `
        -ScriptPath (Join-Path $root 'examples\client-proof\run-client.ps1') `
        -TimeoutSeconds $ProofClientTimeout
    $firstLog = $result.LogPath
    $ok = ($result.ExitCode -eq 0) -and (-not $result.TimedOut) -and
        $result.Output.Contains($markers[0]) -and $result.Output.Contains($markers[1])
    $retried = $false
    if (-not $ok) {
        $retried = $true
        $result = Invoke-RepoScreenScript -Name 'H-client-proof-retry' `
            -ScriptPath (Join-Path $root 'examples\client-proof\run-client.ps1') `
            -TimeoutSeconds $ProofClientTimeout
        $ok = ($result.ExitCode -eq 0) -and (-not $result.TimedOut) -and
            $result.Output.Contains($markers[0]) -and $result.Output.Contains($markers[1])
    }
    $hookMisses = @(Get-WeaverHookMisses -LogPath (Join-Path $root 'examples\client-proof\out\logs\client.log'))
    if ($hookMisses.Count -gt 0) { $ok = $false }
    # The vanilla client data generator under the agent fires InitializeClientRegistriesEvent.
    $datagenFailed = $false
    if ($ok) {
        $datagen = Invoke-RepoScreenScript -Name 'H-client-datagen' `
            -ScriptPath (Join-Path $root 'examples\client-proof\run-datagen.ps1') -TimeoutSeconds $ProofClientTimeout
        if (-not $datagen.Output.Contains('CLIENT DATAGEN PROOF ok=true')) {
            $ok = $false
            $datagenFailed = $true
        }
    }
    $detail = $markers[0]
    if ($retried) {
        $detail = $detail + ' (passed on one retry; first log: ' + $firstLog + ')'
    }
    if ($ok) {
        Write-StepResult -Step 'H-client-proof' -Status 'PASS' -Detail $detail -LogPath $result.LogPath
    } else {
        $reason = 'missing client marker(s)'
        if ($hookMisses.Count -gt 0) { $reason = 'Weaver hook not bound: ' + ($hookMisses -join ' | ') }
        if ($datagenFailed) { $reason = 'client datagen proof failed (examples/client-proof/run-datagen.ps1)' }
        if ($result.TimedOut) { $reason = "timeout=${ProofClientTimeout}s" }
        elseif ($result.ExitCode -ne 0) { $reason = "exit=$($result.ExitCode); " + $reason }
        if ($retried) { $reason = $reason + '; retried once; first log: ' + $firstLog }
        Write-StepResult -Step 'H-client-proof' -Status 'FAIL' -Detail $reason -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    }
}

Invoke-GateStep -Step 'I-shared-mode' -Action {
    if ($SkipSharedProof -or $SkipGameProofs -or $Quick) {
        $script:sharedMode = 'skipped'
        $flag = '-SkipSharedProof'
        if ($SkipGameProofs) { $flag = '-SkipGameProofs' }
        if ($Quick) { $flag = '-Quick' }
        Write-StepResult -Step 'I-shared-mode' -Status 'SKIP' -Detail ("skipped by " + $flag)
        return
    }
    $prerequisites = Test-GameProofPrerequisites
    if (-not $prerequisites.Ok) {
        $script:sharedMode = 'skipped'
        Write-StepResult -Step 'I-shared-mode' -Status 'SKIP' -Detail $prerequisites.Reason
        return
    }
    $proofJar = Join-Path $root 'examples\proof\out\proof.jar'
    if (-not (Test-Path -LiteralPath $proofJar -PathType Leaf)) {
        $build = Invoke-RepoScreenScript -Name 'I-shared-build' `
            -ScriptPath (Join-Path $root 'examples\proof\build.ps1') -TimeoutSeconds $BuildTimeout
        if (($build.ExitCode -ne 0) -or $build.TimedOut -or
            (-not $build.Output.Contains('PROOF BUILD ok=true'))) {
            $script:sharedMode = 'not-observed'
            Write-StepResult -Step 'I-shared-mode' -Status 'FAIL' `
                -Detail 'examples/proof/build.ps1 failed before the shared-mode run' `
                -LogPath $build.LogPath
            Show-FailureOutput -Result $build
            return
        }
    }
    $result = Invoke-RepoScreenScript -Name 'I-shared-mode' `
        -ScriptPath (Join-Path $root 'examples\proof\run-server-shared.ps1') `
        -ScriptArguments @('-AllowDocumentedBlocker') -TimeoutSeconds $SharedTimeout
    $documentedBlocker = 'game-classes-hosted-by-system-loader-shared-transforms-skipped-no-proof-init'
    $probe = $result.Output.Contains('SHARED FACTORY PROBE ok=true')
    $scriptResultPass = $result.Output.Contains('SHARED MODE RESULT ok=true side=server')
    $blocker = ''
    $blockerMatch = [regex]::Match($result.Output,
        'SHARED MODE ACKNOWLEDGED ok=false blocker=([^\s]+)')
    if ($blockerMatch.Success) { $blocker = $blockerMatch.Groups[1].Value }
    $documentedAcknowledged = $blocker -eq $documentedBlocker
    $logPath = ''
    $logMatch = [regex]::Match($result.Output,
        'SHARED MODE RESULT ok=(?:true|false)[^\r\n]*log=([^\r\n]+)')
    if ($logMatch.Success) { $logPath = $logMatch.Groups[1].Value.Trim() }
    if (($logPath -eq '') -or (-not (Test-Path -LiteralPath $logPath -PathType Leaf))) {
        $logPath = Join-Path $root 'examples\proof\out\logs\server-shared.log'
    }
    $serverLog = ''
    if (Test-Path -LiteralPath $logPath -PathType Leaf) {
        $rawLog = Get-Content -LiteralPath $logPath -Raw
        if ($null -ne $rawLog) { $serverLog = $rawLog }
    }
    $sharedMarker = $serverLog.Contains('[weaver] CLASS SPACE mode=shared')
    $agentShared = $serverLog.Contains('[weaver] AGENT READY') -and
        $serverLog.Contains('class-space=shared')
    $proof20 = $serverLog.Contains('PROOF RESULT ok=true side=server checks=20')
    $proofEvidence = $sharedMarker -and $agentShared -and $proof20 -and $probe -and $scriptResultPass
    $observedFacts = ("shared_marker={0} agent_ready_shared={1} proof_20={2} probe={3} result_ok={4} blocker={5}" -f `
        [bool]$sharedMarker, [bool]$agentShared, [bool]$proof20, [bool]$probe,
        [bool]$scriptResultPass, $(if ($blocker -eq '') { '<none>' } else { $blocker }))

    $cleanExit = ($result.ExitCode -eq 0) -and (-not $result.TimedOut)
    if ($documentedAcknowledged -and $proofEvidence) {
        $script:sharedMode = 'fail'
        Write-StepResult -Step 'I-shared-mode' -Status 'FAIL' `
            -Detail ("ambiguous shared-mode output: both the documented blocker and a full proof pass were observed; {0}; server_log={1}" -f $observedFacts, $logPath) `
            -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    } elseif ($documentedAcknowledged -and $probe -and $cleanExit) {
        $script:sharedMode = 'documented-blocker'
        Write-StepResult -Step 'I-shared-mode' -Status 'PASS' `
            -Detail ("shared_mode=documented-blocker; experimental, environment-dependent behavior observed; acknowledged limitation; blocker={0}; SHARED FACTORY PROBE ok=true; shared mode is not supported; server_log={1}" -f $documentedBlocker, $logPath) `
            -LogPath $result.LogPath
    } elseif ($proofEvidence -and $cleanExit) {
        $script:sharedMode = 'proof-pass'
        Write-StepResult -Step 'I-shared-mode' -Status 'PASS' `
            -Detail ("shared_mode=proof-pass; experimental, environment-dependent behavior observed; CLASS SPACE mode=shared; AGENT READY class-space=shared; PROOF RESULT ok=true side=server checks=20; SHARED FACTORY PROBE ok=true; shared mode is not supported; server_log={0}" -f $logPath) `
            -LogPath $result.LogPath
    } else {
        $script:sharedMode = 'fail'
        $reason = 'shared-mode behavior was neither the exact documented blocker nor a complete proof pass'
        if ($result.TimedOut) { $reason = "timeout=${SharedTimeout}s; " + $reason }
        elseif ($result.ExitCode -ne 0) { $reason = "exit=$($result.ExitCode); " + $reason }
        Write-StepResult -Step 'I-shared-mode' -Status 'FAIL' `
            -Detail ("{0}; {1}; server_log={2}" -f $reason, $observedFacts, $logPath) `
            -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    }
}

Invoke-GateStep -Step 'J-api-benchmark' -Action {
    if ($SkipBenchmarks -or $Quick) {
        $flag = '-Quick'
        if ($SkipBenchmarks) { $flag = '-SkipBenchmarks' }
        Write-StepResult -Step 'J-api-benchmark' -Status 'SKIP' -Detail ("skipped by " + $flag)
        return
    }
    $result = Invoke-Gradle -Name 'J-api-benchmark' -GradleArguments @(
        ':api:benchmark'
    ) -TimeoutSeconds $BenchTimeout
    $benchLines = [regex]::Matches($result.Output, '(?m)^BENCH ')
    $ok = ($result.ExitCode -eq 0) -and (-not $result.TimedOut) -and
        ($benchLines.Count -ge 4)
    if ($ok) {
        Write-StepResult -Step 'J-api-benchmark' -Status 'PASS' `
            -Detail ("{0} BENCH lines parsed (minimum 4)" -f $benchLines.Count) `
            -LogPath $result.LogPath
    } else {
        $reason = ("BENCH lines={0}; minimum is 4" -f $benchLines.Count)
        if ($result.TimedOut) { $reason = "timeout=${BenchTimeout}s" }
        elseif ($result.ExitCode -ne 0) { $reason = "exit=$($result.ExitCode); " + $reason }
        Write-StepResult -Step 'J-api-benchmark' -Status 'FAIL' -Detail $reason -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    }
}

Invoke-GateStep -Step 'J-platform-bench' -Action {
    if ($SkipBenchmarks -or $Quick) {
        $flag = '-Quick'
        if ($SkipBenchmarks) { $flag = '-SkipBenchmarks' }
        Write-StepResult -Step 'J-platform-bench' -Status 'SKIP' -Detail ("skipped by " + $flag)
        return
    }
    $result = Invoke-RepoScreenScript -Name 'J-platform-bench' `
        -ScriptPath (Join-Path $root 'examples\proof\bench\run.ps1') `
        -TimeoutSeconds $BenchTimeout
    $marker = 'PLATFORM_BENCH BUDGET ok=true'
    $ok = ($result.ExitCode -eq 0) -and (-not $result.TimedOut) -and $result.Output.Contains($marker)
    if ($ok) {
        Write-StepResult -Step 'J-platform-bench' -Status 'PASS' -Detail $marker -LogPath $result.LogPath
    } else {
        $reason = 'missing marker ' + $marker
        if ($result.TimedOut) { $reason = "timeout=${BenchTimeout}s" }
        elseif ($result.ExitCode -ne 0) { $reason = "exit=$($result.ExitCode); " + $reason }
        Write-StepResult -Step 'J-platform-bench' -Status 'FAIL' -Detail $reason -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    }
}

Invoke-GateStep -Step 'K-diff-check' -Action {
    $result = Invoke-NativeCommand -Name 'K-diff-check' -FileName 'git.exe' `
        -NativeArguments @('diff', '--check') -TimeoutSeconds 120
    $lines = @(($result.Output -split "`r?`n"))
    $errors = @($lines | Where-Object {
        $_.Trim() -ne '' -and
        $_ -notmatch '^warning:' -and
        $_ -notmatch 'LF will be replaced by CRLF'
    })
    $ok = ($result.ExitCode -eq 0) -and (-not $result.TimedOut) -and ($errors.Count -eq 0)
    if ($ok) {
        Write-StepResult -Step 'K-diff-check' -Status 'PASS' `
            -Detail 'no whitespace errors (LF/CRLF warnings ignored)' -LogPath $result.LogPath
    } else {
        $reason = if ($errors.Count -gt 0) {
            'whitespace errors: ' + (($errors | Select-Object -First 10) -join ' | ')
        } else {
            "git diff --check exited $($result.ExitCode) without identifiable whitespace errors"
        }
        Write-StepResult -Step 'K-diff-check' -Status 'FAIL' -Detail $reason -LogPath $result.LogPath
    }
}

Invoke-GateStep -Step 'K-marker-scan' -Action {
    $listing = Invoke-NativeCommand -Name 'K-marker-list' -FileName 'git.exe' `
        -NativeArguments @('ls-files', '--cached', '--others', '--exclude-standard') `
        -TimeoutSeconds 120
    if (($listing.ExitCode -ne 0) -or $listing.TimedOut) {
        Write-StepResult -Step 'K-marker-scan' -Status 'FAIL' `
            -Detail 'git ls-files failed; cannot scan for unfinished-work markers' `
            -LogPath $listing.LogPath
        return
    }
    $policyPage = 'docs/release-checklist.md'
    $extensionPattern = '\.(java|kt|kts|ps1|md|json|xml|properties|gradle|toml|cfg)$'
    $excludedDirectoryPattern = '(^|/)(build|out|fixture-world|\.gradle|logs|work)/'
    $excludedRootPattern = '^(spike|build|\.gradle|logs|out|work)/'
    $files = @($listing.Output -split "`r?`n" | Where-Object {
        $_.Trim() -ne '' -and
        $_ -match $extensionPattern -and
        $_ -notmatch $excludedDirectoryPattern -and
        $_ -notmatch $excludedRootPattern -and
        $_ -ne $policyPage
    })
    $todoToken = 'TO' + 'DO'
    $fixmeToken = 'FI' + 'XME'
    $xxxToken = 'X' + 'XX'
    $stubToken = 'st' + 'ub'
    $unimplementedToken = 'un' + 'implemented'
    $notImplementedToken = 'not ' + 'implemented'
    $markerPattern = '\b' + $todoToken + '\b|\b' + $fixmeToken + '\b|\b' + $xxxToken +
        '\b|(?i:(?://|#|/\*|\*)\s*(?:' + $stubToken + '|' + $unimplementedToken + '|' +
        $notImplementedToken + ')\b)'
    $hits = New-Object System.Collections.Generic.List[string]
    foreach ($relativePath in $files) {
        $absolutePath = Join-Path $root $relativePath
        if (-not (Test-Path -LiteralPath $absolutePath -PathType Leaf)) { continue }
        $lineNumber = 0
        foreach ($line in [System.IO.File]::ReadLines($absolutePath)) {
            $lineNumber++
            if ($line -match $markerPattern) {
                $hits.Add(("{0}:{1}: {2}" -f $relativePath, $lineNumber, $line.Trim()))
                if ($hits.Count -ge 50) { break }
            }
        }
        if ($hits.Count -ge 50) { break }
    }
    $scanLog = Join-Path $captureDir 'K-marker-scan.log'
    if ($hits.Count -eq 0) {
        [System.IO.File]::WriteAllText($scanLog, "no unfinished-work markers in $($files.Count) files")
        Write-StepResult -Step 'K-marker-scan' -Status 'PASS' `
            -Detail ("0 hits in {0} tracked source/docs files; excluded policy page {1}" -f $files.Count, $policyPage) `
            -LogPath $scanLog
    } else {
        [System.IO.File]::WriteAllText($scanLog, ($hits -join [Environment]::NewLine))
        $preview = ($hits | Select-Object -First 10) -join ' | '
        Write-StepResult -Step 'K-marker-scan' -Status 'FAIL' `
            -Detail ("unfinished-work markers: " + $preview) -LogPath $scanLog
    }
}

Invoke-GateStep -Step 'L-patcher' -Action {
    $patcherVerify = Join-Path $root 'tools\patcher\verify.ps1'
    $patcherClient = Join-Path $root 'work\game-26.3\client.jar'
    $patcherServer = Join-Path $root 'work\server-26.3\versions\26.3\server-26.3.jar'
    if (-not (Test-Path -LiteralPath $patcherVerify -PathType Leaf) -or
        -not (Test-Path -LiteralPath $patcherClient -PathType Leaf) -or
        -not (Test-Path -LiteralPath $patcherServer -PathType Leaf)) {
        Write-StepResult -Step 'L-patcher' -Status 'SKIP' `
            -Detail 'patcher or official game jars are missing'
        return
    }
    $result = Invoke-RepoScreenScript -Name 'L-patcher' -ScriptPath $patcherVerify `
        -TimeoutSeconds 600
    $ok = ($result.ExitCode -eq 0) -and (-not $result.TimedOut) -and
        $result.Output.Contains('PATCHER VERIFY ok=true') -and
        $result.Output.Contains('PATCHER CLASSLOAD') -and
        $result.Output.Contains('signatures=stripped') -and
        $result.Output.Contains('PATCHER VERIFY source=ok')
    if ($ok) {
        Write-StepResult -Step 'L-patcher' -Status 'PASS' `
            -Detail 'binary and source patch pipelines: deterministic, signatures stripped, full class-load verification' `
            -LogPath $result.LogPath
    } else {
        $reason = 'missing or failed patcher markers'
        if ($result.TimedOut) { $reason = 'timeout' }
        elseif ($result.ExitCode -ne 0) { $reason = "exit=$($result.ExitCode); " + $reason }
        Write-StepResult -Step 'L-patcher' -Status 'FAIL' -Detail $reason `
            -LogPath $result.LogPath
        Show-FailureOutput -Result $result
    }
}

$elapsed = (Get-Date) - $script:started
$passCount = @($script:results | Where-Object { $_.Status -eq 'PASS' }).Count
$failCount = @($script:results | Where-Object { $_.Status -eq 'FAIL' }).Count
$skipCount = @($script:results | Where-Object { $_.Status -eq 'SKIP' }).Count

Write-Host ''
Write-Host '== Verify-CI summary =='
foreach ($entry in $script:results) {
    $color = 'Red'
    if ($entry.Status -eq 'PASS') { $color = 'Green' }
    if ($entry.Status -eq 'SKIP') { $color = 'Yellow' }
    Write-Host ("[{0}] {1}: {2}" -f $entry.Status, $entry.Step, $entry.Detail) -ForegroundColor $color
}
Write-Host ''
if ($skipCount -gt 0) {
    Write-Host ("skipped steps: " + (($script:results | Where-Object { $_.Status -eq 'SKIP' } |
        ForEach-Object { $_.Step }) -join ', '))
}
$okText = 'false'
if (-not $script:failed) { $okText = 'true' }
Write-Host ("WEAVER VERIFY-CI ok={0} steps={1} pass={2} fail={3} skip={4} shared_mode={5} elapsed={6:n1}s" -f `
    $okText, $script:results.Count, $passCount, $failCount, $skipCount,
    $script:sharedMode, $elapsed.TotalSeconds)

if ($script:failed) { exit 1 }
exit 0
