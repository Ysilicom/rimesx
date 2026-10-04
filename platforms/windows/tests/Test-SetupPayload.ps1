#requires -Version 5.1
param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$info=Join-Path $OutputDirectory 'TestBuildInfo.cs'
'internal static class BuildInfo { public const string Version="1.0.0"; public const string PayloadHash="unused"; }' | Set-Content -LiteralPath $info -Encoding UTF8
$compiler=Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$output=Join-Path $OutputDirectory 'SetupPayloadTests.exe'
& $compiler /nologo /codepage:65001 /target:exe /main:SetupPayloadTests /platform:x64 /warnaserror+ "/out:$output" /reference:System.Windows.Forms.dll /reference:System.Drawing.dll /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll /reference:System.Web.Extensions.dll "$PSScriptRoot\..\setup\Setup.cs" "$PSScriptRoot\SetupPayloadTests.cs" $info
if($LASTEXITCODE){throw 'Installer test compilation failed'}
& $output
if($LASTEXITCODE){throw 'Installer payload tests failed'}
