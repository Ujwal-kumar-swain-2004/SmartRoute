param([string]$Endpoint='https://maps.mail.ru/osm/tools/overpass/api/interpreter')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$query='[out:json][timeout:90];way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$"](20.22,85.75,20.38,85.90);(._;>;);out body;'
$destination=Join-Path $projectRoot 'data\bhubaneswar-osm.json'
New-Item -ItemType Directory -Force (Split-Path -Parent $destination) | Out-Null
$temporary=Join-Path $projectRoot 'data\bhubaneswar-osm.download.json'
Invoke-WebRequest -Uri $Endpoint -Method Post -Body @{data=$query} -UserAgent 'SmartRouteAcademic/1.0 (local university routing demonstration)' -OutFile $temporary -TimeoutSec 110
$parsed=Get-Content -Raw -LiteralPath $temporary | ConvertFrom-Json
if (-not $parsed.elements -or $parsed.remark) { throw 'Provider returned an incomplete extract; existing dataset has been preserved.' }
Move-Item -LiteralPath $temporary -Destination $destination -Force
$resources=Join-Path $projectRoot 'backend\src\main\resources\osm'
New-Item -ItemType Directory -Force $resources | Out-Null
Copy-Item -LiteralPath $destination -Destination (Join-Path $resources 'bhubaneswar.json') -Force
Write-Host "Downloaded $($parsed.elements.Count) OSM elements. Rebuild and restart SmartRoute."


