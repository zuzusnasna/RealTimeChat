$ErrorActionPreference = "Continue"
$root = $PSScriptRoot
$statePath = Join-Path $root ".local-dev-processes.json"

Write-Host "Stopping local Spring Boot and React processes..." -ForegroundColor Cyan
if (Test-Path $statePath) {
    try {
        $state = Get-Content $statePath -Raw | ConvertFrom-Json
        foreach ($pidValue in @($state.frontendPid, $state.backendPid)) {
            if ($pidValue) {
                $process = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
                if ($process) {
                    # Stop only the process started by start-local.ps1; do not stop unrelated apps.
                    Stop-Process -Id ([int]$pidValue) -Force -ErrorAction SilentlyContinue
                }
            }
        }
    } catch { Write-Warning "Could not fully read the saved process list: $($_.Exception.Message)" }
    Remove-Item $statePath -Force -ErrorAction SilentlyContinue
} else {
    Write-Host "No saved local process list found. Checking only whether the local dev ports are in use."
}

Write-Host "Stopping local MySQL and Redis containers..." -ForegroundColor Cyan
docker compose -f (Join-Path $root "docker-compose.local.yml") down
if ($LASTEXITCODE -ne 0) { Write-Warning "Docker Compose did not stop cleanly. You can retry the command manually." }

Write-Host "Local development stop command completed." -ForegroundColor Green
Write-Host "Database data remains in Docker volumes. To remove it, run docker compose -f docker-compose.local.yml down -v from the repository root."
