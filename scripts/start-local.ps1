param([string]$DeviceId = '2a7a1d50', [switch]$OpenApp)
$ErrorActionPreference = 'Stop'
trap {
    Write-Host ("Startup needs attention: " + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
$projectRoot = Split-Path -Parent $PSScriptRoot
$adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adbPath)) { throw 'Android platform-tools are missing.' }
$logDirectory = Join-Path $projectRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'

function Get-OllamaStatus {
    try { Invoke-RestMethod 'http://127.0.0.1:11434/api/version' -TimeoutSec 2 }
    catch { $null }
}

$ollamaStatus = Get-OllamaStatus
if (-not $ollamaStatus) {
    $ollamaCommand = Get-Command ollama -ErrorAction SilentlyContinue
    $ollamaPath = if ($ollamaCommand) { $ollamaCommand.Source } else {
        Join-Path $env:LOCALAPPDATA 'Programs\Ollama\ollama.exe'
    }
    if (-not (Test-Path -LiteralPath $ollamaPath)) { throw 'Ollama is missing. Install Ollama and gemma4:e2b first.' }
    Write-Host 'Starting local Ollama...'
    $previousOllamaHost = $env:OLLAMA_HOST
    try {
        $env:OLLAMA_HOST = '127.0.0.1:11434'
        $ollamaProcess = Start-Process -FilePath $ollamaPath -ArgumentList 'serve' -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $logDirectory "ollama-$stamp.out.log") `
            -RedirectStandardError (Join-Path $logDirectory "ollama-$stamp.err.log")
    } finally { $env:OLLAMA_HOST = $previousOllamaHost }
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $ollamaStatus = Get-OllamaStatus
        if ($ollamaStatus) { break }
        if ($ollamaProcess.HasExited) { throw 'Ollama exited. See the latest Ollama error log in artifacts.' }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ollamaStatus) { throw 'Ollama did not become ready. See artifacts for logs, then retry.' }
} else { Write-Host 'Ollama is already running.' }
if (-not $ollamaStatus.version) { throw 'Port 11434 did not return a valid Ollama response.' }

function Get-BridgeHealth {
    try { Invoke-RestMethod 'http://127.0.0.1:8000/health' -TimeoutSec 2 }
    catch { $null }
}

$health = Get-BridgeHealth
if ($health -and $health.service -ne 'HandsFree AI') { throw 'Port 8000 belongs to another service.' }
if (-not $health) {
    $pythonPath = Join-Path $projectRoot '.venv\Scripts\python.exe'
    if (-not (Test-Path -LiteralPath $pythonPath)) { $pythonPath = (Get-Command python -ErrorAction Stop).Source }
    $process = Start-Process -FilePath $pythonPath -ArgumentList '-m', 'backend.run' `
        -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logDirectory "backend-$stamp.out.log") `
        -RedirectStandardError (Join-Path $logDirectory "backend-$stamp.err.log")
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $health = Get-BridgeHealth
        if ($health) { break }
        if ($process.HasExited) { throw 'Backend exited. See the latest backend error log in artifacts.' }
        Start-Sleep -Milliseconds 500
    }
    if (-not $health -or $health.service -ne 'HandsFree AI') { throw 'Backend did not become ready. See artifacts for logs.' }
    Write-Host "HandsFree backend started (PID $($process.Id)). It stays running when this command ends."
} else { Write-Host 'HandsFree backend is already running.' }
if ($health.status -ne 'ok') { throw 'HandsFree backend is not healthy. See artifacts for logs.' }

try { $model = Invoke-RestMethod 'http://127.0.0.1:8000/model/status' -TimeoutSec 7 }
catch { throw 'The backend cannot reach Ollama. Check the local services and run this launcher again.' }
if (-not $model.available) { throw 'The configured model is missing. For the default setup, run: ollama pull gemma4:e2b' }
Write-Host "Reasoning model available: $($model.model)"

$deviceReady = $false
try {
    $deviceState = & $adbPath -s $DeviceId get-state 2>&1
    $deviceReady = $LASTEXITCODE -eq 0 -and ($deviceState -join '').Trim() -eq 'device'
} catch { $deviceReady = $false }
if (-not $deviceReady) {
    throw 'Phone not ready. Connect USB, unlock it, enable USB debugging and approve its Allow USB debugging prompt. Then rerun this file.'
}
& $adbPath -s $DeviceId reverse tcp:8000 tcp:8000
if ($LASTEXITCODE -ne 0) { throw 'Unlock the phone, reconnect USB, approve USB debugging, then run this command again.' }
Write-Host 'Phone bridge connected. HandsFree will refresh its connection automatically.'
if (-not $health.voice_configured) { Write-Warning 'Voice credentials are missing from .env.local. Text input remains available.' }
if ($OpenApp) {
    $installed = & $adbPath -s $DeviceId shell pm path com.example.handsfree
    if ($LASTEXITCODE -ne 0 -or -not ($installed -match '^package:')) {
        throw 'HandsFree is not installed on this phone. Follow README: Connect and install, then rerun this launcher.'
    }
    $launch = & $adbPath -s $DeviceId shell am start -W -n com.example.handsfree/.MainActivity 2>&1
    if ($LASTEXITCODE -ne 0 -or ($launch -match 'Error:|Exception|Status: timeout')) {
        throw 'Android could not open HandsFree. Unlock the phone and open HandsFree from its app icon.'
    }
    Write-Host 'HandsFree opened on your phone. Check Settings: Screen access is on.'
    Write-Host 'Android permission prompts need your approval; this launcher does not change them.'
}
Write-Host 'Keep the laptop awake and USB connected. Internet is needed for voice and online apps.'
