$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$javaHome = $env:JAVA_HOME
if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome 'bin\jpackage.exe'))) {
    throw 'JDK 17+ with jpackage is required. Set JAVA_HOME to a JDK 17+ installation.'
}

& (Join-Path $PSScriptRoot 'build.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$packageInput = Join-Path $projectRoot 'build-out\package-input'
$outputName = if ($args.Count -gt 0) { $args[0] } else { 'UsbFileShare-dist-' + (Get-Date -Format 'yyyyMMdd-HHmmss') }
$output = Join-Path $projectRoot (Join-Path 'build-out' $outputName)
New-Item -ItemType Directory -Force $packageInput | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'build-out\usb-file-share.jar') -Destination (Join-Path $packageInput 'usb-file-share.jar') -Force
if (Test-Path $output) {
    throw "Package output already exists: $output. Remove it manually before packaging again."
}
& (Join-Path $javaHome 'bin\jpackage.exe') `
    --type app-image `
    --name UsbFileShare `
    --dest $output `
    --input $packageInput `
    --main-jar usb-file-share.jar `
    --main-class com.usbfileshare.Main `
    --java-options '-Dfile.encoding=UTF-8'
if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE" }
Write-Output "Desktop application created: $(Join-Path $output 'UsbFileShare\UsbFileShare.exe')"
