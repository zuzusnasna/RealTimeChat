$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$frontend = Join-Path $root "frontend"
$backendLog = Join-Path $root ".local-backend.log"
$frontendLog = Join-Path $root ".local-frontend.log"
$backendErr = Join-Path $root ".local-backend-error.log"
$frontendErr = Join-Path $root ".local-frontend-error.log"
$composeFile = Join-Path $root "docker-compose.local.yml"

Write-Host ""
Write-Host "[1/5] Checking prerequisites..." -ForegroundColor Cyan
foreach ($command in @("docker", "npm")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "$command was not found. Install/start it, then retry." }
}
if (-not (Test-Path (Join-Path $root "gradlew.bat"))) { throw "gradlew.bat was not found. Run this script from the repository root." }
if (-not (Test-Path (Join-Path $frontend "package.json"))) { throw "frontend/package.json was not found." }
docker info *> $null
if ($LASTEXITCODE -ne 0) { throw "Docker Desktop is not running. Start Docker Desktop and retry." }

Write-Host "[2/5] Starting local MySQL and Redis..." -ForegroundColor Cyan
docker compose -f $composeFile up -d
if ($LASTEXITCODE -ne 0) { throw "Could not start local MySQL/Redis." }

Write-Host "[3/5] Waiting for MySQL and Redis health checks..." -ForegroundColor Cyan
$deadline = (Get-Date).AddMinutes(2)
$mysqlReady = $false
$redisReady = $false
do {
    $mysqlHealth = docker inspect --format "{{.State.Health.Status}}" realtime-chat-local-mysql 2>$null
    $mysqlInspectExit = $LASTEXITCODE
    $redisHealth = docker inspect --format "{{.State.Health.Status}}" realtime-chat-local-redis 2>$null
    $redisInspectExit = $LASTEXITCODE
    $mysqlReady = ($mysqlInspectExit -eq 0 -and "$mysqlHealth".Trim() -eq "healthy")
    $redisReady = ($redisInspectExit -eq 0 -and "$redisHealth".Trim() -eq "healthy")
    if ($mysqlReady -and $redisReady) { break }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)
if (-not ($mysqlReady -and $redisReady)) {
    docker compose -f $composeFile ps
    throw "MySQL/Redis did not become healthy within 2 minutes. Check: docker compose -f docker-compose.local.yml logs mysql redis"
}

Write-Host "[4/5] Installing frontend dependencies if needed..." -ForegroundColor Cyan
if (-not (Test-Path (Join-Path $frontend "node_modules"))) {
    Push-Location $frontend
    try { npm install; if ($LASTEXITCODE -ne 0) { throw "npm install failed." } }
    finally { Pop-Location }
}

Write-Host "[5/5] Starting Spring Boot and React dev server..." -ForegroundColor Cyan
$backendListener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
$frontendListener = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue
if ($backendListener) { throw "Port 8080 is already in use. Stop the existing backend, then run this script again." }
if ($frontendListener) { throw "Port 5173 is already in use. Stop the existing Vite server, then run this script again." }

Remove-Item $backendLog, $frontendLog, $backendErr, $frontendErr -Force -ErrorAction SilentlyContinue
$env:SPRING_PROFILES_ACTIVE = "local"
$backend = Start-Process -FilePath (Join-Path $root "gradlew.bat") -ArgumentList @("bootRun") -WorkingDirectory $root -PassThru -WindowStyle Hidden -RedirectStandardOutput $backendLog -RedirectStandardError $backendErr
$frontendProcess = Start-Process -FilePath "npm.cmd" -ArgumentList @("run", "dev", "--", "--host", "127.0.0.1") -WorkingDirectory $frontend -PassThru -WindowStyle Hidden -RedirectStandardOutput $frontendLog -RedirectStandardError $frontendErr
@{ backendPid = $backend.Id; frontendPid = $frontendProcess.Id; startedAt = (Get-Date).ToString("o") } | ConvertTo-Json | Set-Content -Path (Join-Path $root ".local-dev-processes.json") -Encoding UTF8

Write-Host ""
Write-Host "Local development startup requested." -ForegroundColor Green
Write-Host "React:       http://localhost:5173/ui/"
Write-Host "Spring Boot: http://localhost:8080"
Write-Host "Backend log: $backendLog"
Write-Host "Frontend log: $frontendLog"
Write-Host "Wait for Spring Boot to finish starting. Stop with .\stop-local.ps1"
Write-Host "This script does not stop unrelated containers or Windows services."
