$ErrorActionPreference = 'Stop'
$source = Get-ChildItem -Recurse -Filter *.java src/main/java | ForEach-Object FullName
$output = Join-Path (Split-Path $PSScriptRoot -Parent) 'build-out\windows'
New-Item -ItemType Directory -Force $output | Out-Null
javac -encoding UTF-8 -d $output $source
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }
jar --create --file (Join-Path (Split-Path $output -Parent) 'usb-file-share.jar') -C $output .
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }
Write-Output 'Windows Receiver compiled successfully.'
