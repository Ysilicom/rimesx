#requires -Version 5.1
param([string]$InstallRoot="$env:ProgramFiles\RIMES")
. "$PSScriptRoot\Package.Common.ps1"
Assert-Administrator
$state=Get-Content -LiteralPath "$InstallRoot\state.json" -Raw | ConvertFrom-Json
Assert-OwnedVersion $InstallRoot $state.active | Out-Null
if(-not $state.legacy -or -not $state.legacy.Count){throw 'No legacy registration is recorded'}
Stop-OwnedBroker $state.active
Assert-Unlocked $state.active
foreach($entry in $state.legacy){if((Get-FileHash -LiteralPath $entry.dll -Algorithm SHA256).Hash -ne $entry.sha256){throw 'Legacy DLL checksum mismatch'}}
try{
    foreach($arch in @('x86','x64')){Invoke-Registrar $state.active $arch 'unregister'}
    foreach($entry in $state.legacy){Invoke-LegacyRegistrar $state.active $entry 'register'}
    $run='HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'
    if($state.previousAutostart){Set-ItemProperty -LiteralPath $run -Name RimesBroker -Value $state.previousAutostart}
    else{Remove-ItemProperty -LiteralPath $run -Name RimesBroker -ErrorAction SilentlyContinue}
    Move-Item -LiteralPath "$InstallRoot\state.json" -Destination "$InstallRoot\legacy-restored-state.json" -Force
}catch{
    foreach($entry in $state.legacy){try{Invoke-LegacyRegistrar $state.active $entry 'unregister'}catch{Write-Warning $_}}
    foreach($arch in @('x64','x86')){Invoke-Registrar $state.active $arch 'register'}
    throw 'Legacy rollback failed; the preview registration was restored.'
}
Write-Output 'Restored the exact previous DLL paths and startup setting. User data retained. Sign out before daily use if any host used the preview.'
