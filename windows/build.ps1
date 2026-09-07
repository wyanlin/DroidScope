$ErrorActionPreference = 'Stop'
node (Join-Path (Split-Path $PSScriptRoot -Parent) 'scripts\build-local-core.mjs')
if ($LASTEXITCODE -ne 0) { throw "build failed with exit code $LASTEXITCODE" }
