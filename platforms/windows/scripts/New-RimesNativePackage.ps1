#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Commit,
    [Parameter(Mandatory)][string]$SharedData,
    [Parameter(Mandatory)][string]$RimeDll,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$Version='0.2.0-preview.1'
)
Set-StrictMode -Version 3.0
$ErrorActionPreference='Stop'
if($Commit -notmatch '^[0-9a-f]{40}$' -or $Version -notmatch '^[a-zA-Z0-9._-]{1,64}$'){throw 'Invalid version or commit'}
$windowsRoot=Split-Path -Parent $PSScriptRoot
$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
$stage=Join-Path $OutputDirectory ('RIMES-Windows-'+$Version)
if(Test-Path -LiteralPath $stage){throw 'Use a new output directory'}
python "$PSScriptRoot\prepare-native-data.py" verify $SharedData
if($LASTEXITCODE){throw 'Product data integrity check failed'}
New-Item -ItemType Directory -Path $stage -Force | Out-Null
foreach($arch in @('x64','x86')){
    $bin=Join-Path $windowsRoot "native\out\build\windows-$arch\Release"
    $identity=Get-Content -LiteralPath "$bin\build-identity.json" -Raw | ConvertFrom-Json
    if($identity.commit -ne $Commit){throw "Built source commit mismatch: $arch"}
    $target=Join-Path $stage $arch
    New-Item -ItemType Directory -Path $target | Out-Null
    foreach($name in @('RimesTsf.dll','RimesRegistrar.exe','RimesTsfTestHost.exe','rimes-windows-registration.json','build-identity.json')){Copy-Item -LiteralPath (Join-Path $bin $name) -Destination $target}
    if($arch -eq 'x64'){Copy-Item -LiteralPath "$bin\RimesBroker.exe" -Destination $target}
}
Copy-Item -LiteralPath $RimeDll -Destination "$stage\x64\rime.dll"
Copy-Item -LiteralPath $SharedData -Destination "$stage\x64\shared" -Recurse
Copy-Item -Path "$windowsRoot\installer\*" -Destination $stage
Copy-Item -LiteralPath "$windowsRoot\native\third_party\licenses" -Destination "$stage\licenses" -Recurse
Copy-Item -LiteralPath "$windowsRoot\native\librime\librime-windows.lock.json" -Destination "$stage\librime-windows.lock.json"
Copy-Item -LiteralPath "$windowsRoot\native\third_party\nlohmann\LICENSE.MIT" -Destination "$stage\LICENSE-nlohmann-json.txt"
Copy-Item -LiteralPath "$windowsRoot\native\third_party\nlohmann\README.md" -Destination "$stage\THIRD-PARTY-json.md"
Copy-Item -LiteralPath "$windowsRoot\..\..\LICENSE" -Destination "$stage\LICENSE-RIMES.txt"
$files=@(Get-ChildItem -LiteralPath $stage -Recurse -File | Sort-Object FullName | ForEach-Object {
    [ordered]@{path=$_.FullName.Substring($stage.Length+1).Replace('\','/');bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
})
[ordered]@{formatVersion=1;product='RIMES';protocol=2;version=$Version;commit=$Commit;createdAt=(Get-Date).ToString('o');architectures=@('x64','x86');brokerArchitecture='x64';files=$files} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$stage\PACKAGE.json" -Encoding UTF8
. "$stage\Package.Common.ps1"
Read-VerifiedPackage $stage | Out-Null
$zip=$stage+'.zip'
Compress-Archive -Path "$stage\*" -DestinationPath $zip
$hash=(Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText($zip+'.sha256',$hash+'  '+[IO.Path]::GetFileName($zip)+[Environment]::NewLine)
[pscustomobject]@{Archive=$zip;SHA256=$hash;Commit=$Commit;Version=$Version;Staging=$stage}
