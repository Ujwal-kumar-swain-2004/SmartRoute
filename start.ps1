param([int]$Port = 8081)
$ErrorActionPreference = 'Stop'
$jar = Join-Path $PSScriptRoot 'backend\target\smartroute-1.0.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the app first with .\build.ps1' }
if ([string]::IsNullOrWhiteSpace($env:TOMTOM_API_KEY)) {
    $secret = Read-Host 'TomTom API key (hidden; press Enter for demo only)' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try { $env:TOMTOM_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
Write-Host "Starting SmartRoute at http://127.0.0.1:$Port. Key is held in this process only."
java -jar $jar "--server.port=$Port" --logging.level.root=INFO --debug=false
