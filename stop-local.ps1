$ErrorActionPreference = "Continue"
$root = [System.IO.Path]::GetFullPath($PSScriptRoot).TrimEnd('\')
$statePath = Join-Path $root ".local-dev-processes.json"
$stoppedPids = [System.Collections.Generic.HashSet[int]]::new()

function Stop-ProcessTree([int]$ProcessId) {
    if ($ProcessId -le 0 -or $stoppedPids.Contains($ProcessId)) { return }
    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($process) {
        Write-Host "Stopping $($process.ProcessName) (PID $ProcessId)..." -ForegroundColor Yellow
        & taskkill.exe /PID $ProcessId /T /F 2>$null | Out-Null
        [void]$stoppedPids.Add($ProcessId)
    }
}

function Get-PortListeners([int]$Port) {
    @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique)
}

Write-Host "Stopping local Spring Boot and React processes..." -ForegroundColor Cyan

# First stop the process trees recorded by start-local.ps1.
if (Test-Path $statePath) {
    try {
        $state = Get-Content $statePath -Raw | ConvertFrom-Json
        foreach ($pidValue in @($state.frontendPid, $state.backendPid)) {
            if ($null -ne $pidValue -and "$pidValue" -match '^\d+$') {
                Stop-ProcessTree -ProcessId ([int]$pidValue)
            }
        }
    } catch {
        Write-Warning "Could not fully read the saved process list: $($_.Exception.Message)"
    }
} else {
    Write-Host "No saved process list found; checking for orphaned processes owned by this project."
}

# Start-Process can leave the actual Java/Node child alive after its saved parent exits.
# Only stop processes whose command line identifies this repository and the expected app.
try {
    $projectProcesses = Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object {
        $_.ProcessId -ne $PID -and
        $_.CommandLine -and
        $_.CommandLine.IndexOf($root, [System.StringComparison]::OrdinalIgnoreCase) -ge 0 -and
        (
            $_.CommandLine -match 'com\.realtimechat\.RealTimeChatApplication' -or
            ($_.CommandLine -match '\b(vite|npm)\b' -and $_.CommandLine -match 'frontend')
        )
    }

    foreach ($projectProcess in $projectProcesses) {
        Stop-ProcessTree -ProcessId ([int]$projectProcess.ProcessId)
    }
} catch {
    Write-Warning "Could not scan for orphaned project processes: $($_.Exception.Message)"
}

# Wait briefly for the application ports to be released, without touching unrelated apps.
$deadline = (Get-Date).AddSeconds(20)
do {
    $backendPids = Get-PortListeners -Port 8080
    $frontendPids = Get-PortListeners -Port 5173
    if ($backendPids.Count -eq 0 -and $frontendPids.Count -eq 0) { break }
    Start-Sleep -Seconds 1
} while ((Get-Date) -lt $deadline)

$remaining = @()
foreach ($port in @(8080, 5173)) {
    foreach ($listenerPid in (Get-PortListeners -Port $port)) {
        $details = Get-CimInstance Win32_Process -Filter "ProcessId = $listenerPid" -ErrorAction SilentlyContinue
        $remaining += [PSCustomObject]@{
            Port = $port
            PID = $listenerPid
            Name = $details.Name
            CommandLine = $details.CommandLine
        }
    }
}
if ($remaining.Count -gt 0) {
    Write-Warning "Some ports are still occupied. They were not forcibly stopped because they may belong to another application:"
    $remaining | Format-List | Out-Host
} else {
    Write-Host "Ports 8080 and 5173 are free." -ForegroundColor Green
}

Remove-Item $statePath -Force -ErrorAction SilentlyContinue

Write-Host "Stopping local MySQL and Redis containers..." -ForegroundColor Cyan
docker compose -f (Join-Path $root "docker-compose.local.yml") down
if ($LASTEXITCODE -ne 0) { Write-Warning "Docker Compose did not stop cleanly. You can retry the command manually." }

Write-Host "Local development stop command completed." -ForegroundColor Green
Write-Host "Database data remains in Docker volumes. To remove it, run docker compose -f docker-compose.local.yml down -v from the repository root."
