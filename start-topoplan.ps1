param([switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

# Load simple KEY=VALUE entries without executing their contents.
$localConfig = Join-Path $root '.env'
if (Test-Path -LiteralPath $localConfig) {
 foreach ($entry in Get-Content -LiteralPath $localConfig -Encoding UTF8) {
  if ($entry -match '^\s*([A-Z][A-Z0-9_]*)=(.*)$') {
   if (-not [Environment]::GetEnvironmentVariable($matches[1], 'Process')) {
    [Environment]::SetEnvironmentVariable($matches[1], $matches[2].Trim(), 'Process')
   }
  }
 }
}
foreach ($requiredSetting in @('TOPO_DB_PASSWORD','TOPO_CONFIG_ENCRYPTION_KEY','TOPO_JWT_SECRET','TOPO_ADMIN_PASSWORD')) {
 $setting = [Environment]::GetEnvironmentVariable($requiredSetting, 'Process')
 if ([string]::IsNullOrWhiteSpace($setting) -or $setting.StartsWith('CHANGE_ME')) {
  throw "Configure $requiredSetting in .env before starting. See README.md."
 }
}

$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
function Test-Port([int]$Port) {
 $client = New-Object Net.Sockets.TcpClient
 try { $task = $client.ConnectAsync('127.0.0.1', $Port); if (-not $task.Wait(800)) { return $false }; return $client.Connected } catch { return $false } finally { $client.Dispose() }
}
function Test-Ready([string]$Url, [string]$Kind) {
 try {
  $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
  if ($Kind -eq 'backend') { $body = $r.Content | ConvertFrom-Json; return ($body.data -is [string] -and $body.data.Length -gt 10) }
  return $r.Content -match '/@vite/client'
 } catch { return $false }
}
function Start-Worker([string]$Name, [string]$Runner) {
 # Give each service its own console so closing the launcher cannot stop it.
 $runnerPath = (Join-Path $root $Runner).Replace("'", "''")
 $outPath = (Join-Path $logDir "$Name-startup.log").Replace("'", "''")
 $errPath = (Join-Path $logDir "$Name-startup.error.log").Replace("'", "''")
 $workerCode = "& '$runnerPath' 1> '$outPath' 2> '$errPath'; exit " + '$LASTEXITCODE'
 $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($workerCode))
 return Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', $encoded) -WorkingDirectory $root -WindowStyle Hidden -PassThru
}
function Wait-Ready([string]$Url, [string]$Kind, $Worker) {
 $deadline = (Get-Date).AddSeconds(180)
 while ((Get-Date) -lt $deadline) {
  if (Test-Ready $Url $Kind) { return }
  if ($Worker -and $Worker.HasExited) { throw "$Kind stopped. Check $logDir\$Kind-startup.log and $Kind-startup.error.log" }
  Start-Sleep -Seconds 1
 }
 throw "$Kind did not become ready in 180 seconds. Check $logDir."
}
$lock = New-Object Threading.Mutex($false, 'Local\TopoPlan-OneClick-5173')
$ownsLock = $false
try {
 $ownsLock = $lock.WaitOne(0)
 if (-not $ownsLock) { Write-Host 'TopoPlan is already starting. Please wait.'; exit 0 }
 foreach ($file in @('frontend\package.json', 'backend\demo1\pom.xml', 'run-topoplan-backend.bat', 'run-topoplan-frontend.bat')) {
  if (-not (Test-Path (Join-Path $root $file))) { throw "Project file missing: $file" }
 }
 foreach ($command in @('java.exe', 'npm.cmd')) {
  if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "Required program missing: $command" }
 }
 # Maven uses JAVA_HOME, which may point to an older JDK than PATH.
 $javaPath = (Get-Command java.exe).Source
 $javaRoot = Split-Path (Split-Path $javaPath -Parent) -Parent
 if (Test-Path (Join-Path $javaRoot 'javac.exe')) { $javaRoot = Split-Path $javaRoot -Parent }
 $jdkCandidates = @($javaRoot, $env:JAVA_HOME) + @(Get-ChildItem 'C:\Program Files\Eclipse Adoptium\jdk-21*', 'C:\Program Files\Java\jdk-21*' -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
 $jdk = $jdkCandidates | Where-Object { $_ -and (Test-Path "$_\bin\javac.exe") -and (Test-Path "$_\release") -and ((Get-Content "$_\release" -Raw) -match 'JAVA_VERSION="21\.') } | Select-Object -First 1
 if (-not $jdk) { throw 'JDK 21 was not found. Install JDK 21 or set JAVA_HOME to it.' }
 $env:JAVA_HOME = $jdk
 $env:PATH = "$jdk\bin;$env:PATH"
 if (-not (Get-Command mvn.cmd -ErrorAction SilentlyContinue)) {
  $candidates = @($env:MAVEN_HOME, $env:M2_HOME) + @(Get-ChildItem 'C:\Program Files\apache-maven-*', "$env:LOCALAPPDATA\Programs\Apache\Maven\apache-maven-*" -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
  foreach ($candidate in $candidates) { if ($candidate -and (Test-Path "$candidate\bin\mvn.cmd")) { $env:PATH = "$candidate\bin;$env:PATH"; break } }
  if (-not (Get-Command mvn.cmd -ErrorAction SilentlyContinue)) { throw 'Maven was not found. Install Maven or set MAVEN_HOME.' }
 }
 if (-not (Test-Path (Join-Path $root 'frontend\node_modules\vite\bin\vite.js'))) {
  Write-Host 'Installing frontend dependencies...'
  Push-Location (Join-Path $root 'frontend')
  try { & npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' } } finally { Pop-Location }
 }
 if (-not $env:TOPO_DB_URL -and -not (Test-Port 3306)) {
  Write-Host 'Starting MySQL...'
  $mysql = Get-Service | Where-Object { $_.Name -match 'mysql' } | Select-Object -First 1
  if (-not $mysql) { throw 'MySQL is not running on port 3306. Start local MySQL and try again.' }
  Start-Service -Name $mysql.Name
  $deadline = (Get-Date).AddSeconds(30)
  while (-not (Test-Port 3306) -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 1 }
  if (-not (Test-Port 3306)) { throw 'MySQL did not start on port 3306.' }
 }
 $backendPort = if ($env:PORT) { [int]$env:PORT } else { 8080 }
 $backendUrl = "http://127.0.0.1:$backendPort/api/auth/csrf"
 $frontendUrl = 'http://localhost:5173'
 $backend = $null
 $frontend = $null
 if (-not (Test-Ready $backendUrl 'backend')) {
  if (Test-Port $backendPort) { throw "Port $backendPort is occupied but TopoPlan API is not ready." }
  Write-Host 'Starting TopoPlan backend...'
  $backend = Start-Worker 'backend' 'run-topoplan-backend.bat'
 }
 if (-not (Test-Ready $frontendUrl 'frontend')) {
  if (Test-Port 5173) { throw 'Port 5173 is occupied by another service.' }
  Write-Host 'Starting TopoPlan frontend...'
  $env:VITE_API_PROXY_TARGET = "http://127.0.0.1:$backendPort"
  $frontend = Start-Worker 'frontend' 'run-topoplan-frontend.bat'
 }
 Write-Host 'Waiting for TopoPlan to be ready...'
 Wait-Ready $backendUrl 'backend' $backend
 Wait-Ready $frontendUrl 'frontend' $frontend
 Wait-Ready "$frontendUrl/api/auth/csrf" 'backend' $frontend
 Write-Host "TopoPlan is ready: $frontendUrl"
 if (-not $NoBrowser) { Start-Process $frontendUrl }
} catch {
 Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
 exit 1
} finally {
 if ($ownsLock) { $lock.ReleaseMutex() }
 $lock.Dispose()
}
