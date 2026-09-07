$ErrorActionPreference = 'Stop'
node (Join-Path (Split-Path $PSScriptRoot -Parent) 'scripts\package-local-core.mjs')
if ($LASTEXITCODE -ne 0) { throw "package failed with exit code $LASTEXITCODE" }
