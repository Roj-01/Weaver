$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path $root "out"
$logs = Join-Path $out "logs"
$jarPath = Join-Path $out "network-proof.jar"
$classPathFile = Join-Path $out "classpath.txt"
$buildScript = Join-Path $root "build.ps1"

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates = @(& (Join-Path $PSScriptRoot '..\..\tools\jdk.ps1')) + $candidates + @("C:\Program Files\Java\jdk-26.0.1")
    foreach ($candidate in $candidates) {
        $java = Join-Path $candidate "bin\java.exe"
        if (Test-Path -LiteralPath $java) { return $candidate }
    }
    throw "No JDK 26 found. Set JAVA_HOME or install C:\Program Files\Java\jdk-26.0.1."
}

function Show-Tail([string]$path) {
    if (Test-Path -LiteralPath $path) {
        Write-Host "----- tail: $path -----"
        Get-Content -LiteralPath $path -Tail 40
    }
}

function Invoke-ProofProcess([string]$mainClass, [string[]]$mainArguments,
                             [string]$logPath, [string]$errPath, [int]$timeoutSeconds) {
    $argumentLine = "-cp " + $classPath + " " + $mainClass
    foreach ($argument in $mainArguments) {
        $argumentLine += " " + $argument
    }
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $java
    $startInfo.Arguments = $argumentLine
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $finished = $process.WaitForExit($timeoutSeconds * 1000)
    if (-not $finished) {
        $process.Kill()
        $process.WaitForExit()
        [System.IO.File]::WriteAllText($logPath, $stdoutTask.Result)
        [System.IO.File]::WriteAllText($errPath, $stderrTask.Result)
        throw "$mainClass did not finish within $timeoutSeconds seconds"
    }
    $process.WaitForExit()
    [System.IO.File]::WriteAllText($logPath, $stdoutTask.Result)
    [System.IO.File]::WriteAllText($errPath, $stderrTask.Result)
    return $process
}

function Test-ProofStale {
    # Rebuild when the proof jar is missing or older than any proof source or any
    # Weaver jar it compiles against; otherwise an API change silently runs stale
    # classes from an earlier build.
    if (-not (Test-Path -LiteralPath $jarPath) -or -not (Test-Path -LiteralPath $classPathFile)) {
        return $true
    }
    $built = (Get-Item -LiteralPath $jarPath).LastWriteTimeUtc
    $repoRoot = Split-Path -Parent (Split-Path -Parent $root)
    $inputs = @(Get-ChildItem -LiteralPath (Join-Path $root 'src') -Recurse -File -ErrorAction SilentlyContinue)
    foreach ($lib in @('api\build\libs', 'core\build\libs', 'api-game\build\libs')) {
        $libDir = Join-Path $repoRoot $lib
        if (Test-Path -LiteralPath $libDir) {
            $inputs += @(Get-ChildItem -LiteralPath $libDir -Filter *.jar -File -ErrorAction SilentlyContinue)
        }
    }
    return @($inputs | Where-Object { $_.LastWriteTimeUtc -gt $built }).Count -gt 0
}

if (Test-ProofStale) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $buildScript
    if ($LASTEXITCODE -ne 0) {
        Write-Host "NETWORK PROOF RUN ok=false reason=build-failed"
        exit 1
    }
}

$jdk = Find-Jdk
$java = Join-Path $jdk "bin\java.exe"
$classPath = (Get-Content -LiteralPath $classPathFile -Raw).Trim()
if (-not $classPath) {
    throw "empty proof classpath: $classPathFile"
}

New-Item -ItemType Directory -Path $logs -Force | Out-Null
$serverLog = Join-Path $logs "server.log"
$serverErr = Join-Path $logs "server.err"
$clientLog = Join-Path $logs "client.log"
$clientErr = Join-Path $logs "client.err"
$mismatchLog = Join-Path $logs "mismatch.log"
$mismatchErr = Join-Path $logs "mismatch.err"
$bridgeLog = Join-Path $logs "bridge-contract.log"
$bridgeErr = Join-Path $logs "bridge-contract.err"
foreach ($path in @($serverLog, $serverErr, $clientLog, $clientErr, $mismatchLog,
        $mismatchErr, $bridgeLog, $bridgeErr)) {
    Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
}

$server = $null
try {
    $bridgeClass = Join-Path $out "classes\net\weaver\networkproof\BridgeContractProof.class"
    if (Test-Path -LiteralPath $bridgeClass) {
        $bridge = Invoke-ProofProcess "net.weaver.networkproof.BridgeContractProof" @() `
            $bridgeLog $bridgeErr 30
        $bridgeOutput = Get-Content -LiteralPath $bridgeLog -Raw -ErrorAction SilentlyContinue
        if ($bridge.ExitCode -ne 0) {
            throw "BridgeContractProof exited with code $($bridge.ExitCode)"
        }
        if (-not $bridgeOutput -or
                -not $bridgeOutput.Contains("NETWORK BRIDGE PROOF RESULT ok=true")) {
            throw "bridge contract log is missing 'NETWORK BRIDGE PROOF RESULT ok=true'"
        }
        $bridgeChecks = ([regex]::Matches($bridgeOutput,
                "NETWORK BRIDGE PROOF CHECK ok=true")).Count
        Write-Host "NETWORK BRIDGE PROOF RESULT ok=true checks=$bridgeChecks"
    }
    else {
        Write-Host "NETWORK BRIDGE PROOF SKIP ok=true reason=bridge-contract-not-built"
    }

    $server = Start-Process -FilePath $java `
        -ArgumentList @("-cp", $classPath, "net.weaver.networkproof.ProofServer") `
        -RedirectStandardOutput $serverLog -RedirectStandardError $serverErr `
        -NoNewWindow -PassThru
    Write-Host "NETWORK SERVER START pid=$($server.Id) log=$serverLog"

    $readyPattern = "NETWORK SERVER READY port=(\d+)"
    $deadline = (Get-Date).AddSeconds(20)
    $port = $null
    while ((Get-Date) -lt $deadline) {
        if ($server.HasExited) { break }
        if (Test-Path -LiteralPath $serverLog) {
            $content = Get-Content -LiteralPath $serverLog -Raw -ErrorAction SilentlyContinue
            if ($content) {
                $match = [regex]::Match($content, $readyPattern)
                if ($match.Success) {
                    $port = [int]$match.Groups[1].Value
                    break
                }
            }
        }
        Start-Sleep -Milliseconds 100
    }
    if (-not $port) {
        throw "server did not print 'NETWORK SERVER READY port=<n>' within 20s"
    }
    Write-Host "NETWORK SERVER READY port=$port"

    $client = Invoke-ProofProcess "net.weaver.networkproof.ProofClient" @("$port") `
        $clientLog $clientErr 30
    $clientOutput = Get-Content -LiteralPath $clientLog -Raw -ErrorAction SilentlyContinue
    if ($client.ExitCode -ne 0) {
        throw "ProofClient exited with code $($client.ExitCode)"
    }
    if (-not $clientOutput -or -not $clientOutput.Contains("NETWORK PROOF RESULT ok=true")) {
        throw "client log is missing 'NETWORK PROOF RESULT ok=true'"
    }
    $clientChecks = ([regex]::Matches($clientOutput, "NETWORK PROOF CHECK ok=true")).Count
    if ($clientChecks -ne 6) {
        throw "client reported $clientChecks successful checks, expected 6"
    }
    Write-Host "NETWORK CLIENT RESULT ok=true checks=$clientChecks"

    $mismatch = Invoke-ProofProcess "net.weaver.networkproof.ProofMismatch" @("$port") `
        $mismatchLog $mismatchErr 30
    $mismatchOutput = Get-Content -LiteralPath $mismatchLog -Raw -ErrorAction SilentlyContinue
    if ($mismatch.ExitCode -ne 0) {
        throw "ProofMismatch exited with code $($mismatch.ExitCode)"
    }
    if (-not $mismatchOutput -or -not $mismatchOutput.Contains("NETWORK MISMATCH RESULT ok=true")) {
        throw "mismatch log is missing 'NETWORK MISMATCH RESULT ok=true'"
    }
    $expectedReason = "NETWORK MISMATCH CHECK ok=true reason=handshake rejected: protocol mismatch: client=2, server=1"
    if (-not $mismatchOutput.Contains($expectedReason)) {
        throw "mismatch log is missing the exact protocol mismatch reason marker"
    }
    Write-Host "NETWORK MISMATCH RESULT ok=true"

    $serverResult = $false
    $serverDeadline = (Get-Date).AddSeconds(10)
    while ((Get-Date) -lt $serverDeadline) {
        $serverOutput = Get-Content -LiteralPath $serverLog -Raw -ErrorAction SilentlyContinue
        if ($serverOutput -and $serverOutput.Contains("NETWORK SERVER RESULT ok=true")) {
            $serverResult = $true
            break
        }
        if ($server.HasExited) { break }
        Start-Sleep -Milliseconds 100
    }
    if (-not $serverResult) {
        throw "server log is missing 'NETWORK SERVER RESULT ok=true'"
    }
    Write-Host "NETWORK SERVER RESULT ok=true"

    Write-Host "NETWORK PROOF RUN ok=true server=ok client=ok mismatch=ok"
}
catch {
    Write-Host "NETWORK PROOF RUN ok=false reason=$($_.Exception.Message)"
    Show-Tail $serverLog
    Show-Tail $serverErr
    Show-Tail $clientLog
    Show-Tail $clientErr
    Show-Tail $mismatchLog
    Show-Tail $mismatchErr
    exit 1
}
finally {
    if ($null -ne $server -and -not $server.HasExited) {
        Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
        $server.WaitForExit(5000) | Out-Null
    }
}
