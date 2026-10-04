#requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('x64', 'x86')]
    [string]$Architecture = 'x64',

    [string]$LockPath,
    [string]$OutputDirectory,
    [string]$CacheDirectory
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

function Get-RimesSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Find-RimesSevenZip {
    $candidates = @(
        (Join-Path $env:ProgramFiles '7-Zip/7z.exe'),
        (Join-Path ${env:ProgramFiles(x86)} '7-Zip/7z.exe')
    )
    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and
            (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return $candidate
        }
    }
    $command = Get-Command 7z -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -ne $command) {
        return $command.Source
    }
    throw '7-Zip is required to extract the pinned librime MSVC archive. Install 7-Zip or add 7z.exe to PATH.'
}

if ([string]::IsNullOrWhiteSpace($LockPath)) {
    $LockPath = Join-Path $PSScriptRoot '../native/librime/librime-windows.lock.json'
}
$lockFile = [System.IO.Path]::GetFullPath($LockPath)
if (-not (Test-Path -LiteralPath $lockFile -PathType Leaf)) {
    throw "librime lock file is missing: $lockFile"
}

$lock = Get-Content -LiteralPath $lockFile -Raw -Encoding UTF8 | ConvertFrom-Json
if ([int]$lock.formatVersion -ne 1) {
    throw "Unsupported librime lock formatVersion: $($lock.formatVersion)"
}
$artifact = $lock.artifacts.$Architecture
if ($null -eq $artifact) {
    throw "Lock file has no artifact for architecture $Architecture"
}

if ([string]::IsNullOrWhiteSpace($CacheDirectory)) {
    $CacheDirectory = Join-Path ([System.IO.Path]::GetTempPath()) 'rimes-librime-cache'
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path ([System.IO.Path]::GetTempPath()) "rimes-librime-$Architecture"
}

$cacheRoot = [System.IO.Path]::GetFullPath($CacheDirectory)
$outputRoot = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $cacheRoot -Force | Out-Null
if (Test-Path -LiteralPath $outputRoot) {
    Remove-Item -LiteralPath $outputRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null

$archivePath = Join-Path $cacheRoot $artifact.name
$expectedHash = [string]$artifact.sha256
if (Test-Path -LiteralPath $archivePath -PathType Leaf) {
    $existingHash = Get-RimesSha256 -Path $archivePath
    if ($existingHash -ne $expectedHash) {
        Remove-Item -LiteralPath $archivePath -Force
    }
}
if (-not (Test-Path -LiteralPath $archivePath -PathType Leaf)) {
    Write-Host "Downloading $($artifact.url)"
    Invoke-WebRequest -Uri $artifact.url -OutFile $archivePath -UseBasicParsing
}
$actualHash = Get-RimesSha256 -Path $archivePath
if ($actualHash -ne $expectedHash) {
    throw "SHA256 mismatch for $($artifact.name). expected=$expectedHash actual=$actualHash"
}

$extractRoot = Join-Path $cacheRoot ("extract-" + $Architecture)
if (Test-Path -LiteralPath $extractRoot) {
    Remove-Item -LiteralPath $extractRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $extractRoot -Force | Out-Null
$sevenZip = Find-RimesSevenZip
$extractOutput = @(& $sevenZip @('x', '-y', "-o$extractRoot", $archivePath) 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "7-Zip extraction failed: $($extractOutput -join ' ')"
}

$dllRelative = [string]$artifact.dllRelativePath
$extractedDll = Join-Path $extractRoot ($dllRelative -replace '/', [System.IO.Path]::DirectorySeparatorChar)
if (-not (Test-Path -LiteralPath $extractedDll -PathType Leaf)) {
    throw "Extracted archive is missing $dllRelative"
}
Copy-Item -LiteralPath $extractedDll -Destination (Join-Path $outputRoot 'rime.dll') -Force

$sidecarNames = @('rime.pdb', 'rime-lua.dll', 'rime-octagram.dll', 'rime-predict.dll')
$extractedDir = Split-Path -Parent $extractedDll
foreach ($sidecar in $sidecarNames) {
    $sidecarPath = Join-Path $extractedDir $sidecar
    if (Test-Path -LiteralPath $sidecarPath -PathType Leaf) {
        Copy-Item -LiteralPath $sidecarPath -Destination (Join-Path $outputRoot $sidecar) -Force
    }
}

$versionPath = Join-Path $outputRoot 'VERSION.txt'
@(
    "source=$($lock.source)",
    "tag=$($lock.tag)",
    "commit=$($lock.commit)",
    "architecture=$Architecture",
    "archive=$($artifact.name)",
    "sha256=$actualHash"
) | Set-Content -LiteralPath $versionPath -Encoding utf8

[pscustomobject]@{
    Architecture = $Architecture
    OutputDirectory = $outputRoot
    DllPath = Join-Path $outputRoot 'rime.dll'
    Archive = $archivePath
    Sha256 = $actualHash
    Source = [string]$lock.source
    Tag = [string]$lock.tag
}
