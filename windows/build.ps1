$ErrorActionPreference = 'Stop'
$source = Get-ChildItem -Recurse -Filter *.java src/main/java | ForEach-Object FullName
$output = Join-Path (Split-Path $PSScriptRoot -Parent) 'build-out\windows'
New-Item -ItemType Directory -Force $output | Out-Null
javac -d $output $source
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }
Write-Output 'Windows Receiver compiled successfully.'
