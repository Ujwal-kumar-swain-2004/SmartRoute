param([switch]$SkipInstall)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
Push-Location (Join-Path $projectRoot 'frontend')
try {
    if (-not $SkipInstall) { npm ci; if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' } }
    npm test
    if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed' }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
} finally { Pop-Location }
$staticPath = Join-Path $projectRoot 'backend\src\main\resources\static'
New-Item -ItemType Directory -Force -Path $staticPath | Out-Null
Copy-Item -Path (Join-Path $projectRoot 'frontend\dist\*') -Destination $staticPath -Recurse -Force
Push-Location (Join-Path $projectRoot 'backend')
try {
    mvn package
    if ($LASTEXITCODE -ne 0) { throw 'Backend build or tests failed' }
} finally { Pop-Location }
Write-Host 'Built backend\target\smartroute-1.0.0.jar. Run: java -jar backend\target\smartroute-1.0.0.jar --server.port=8081'
