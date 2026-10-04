#requires -Version 5.1
param([string]$InstallRoot="$env:ProgramFiles\RIMES")
. "$PSScriptRoot\Package.Common.ps1"
Assert-Administrator
$state=Get-Content -LiteralPath "$InstallRoot\state.json" -Raw | ConvertFrom-Json
Assert-OwnedVersion $InstallRoot $state.active | Out-Null
if(-not (Test-Path -LiteralPath "$InstallRoot\legacy-recovery.json")){throw 'No legacy registration is recorded'}
$recovery=Get-Content -LiteralPath "$InstallRoot\legacy-recovery.json" -Raw | ConvertFrom-Json
$legacy=@($recovery.entries)
if(-not $legacy.Count){throw 'No legacy registration is recorded'}
Stop-OwnedBroker $state.active
Assert-Unlocked $state.active
foreach($entry in $legacy){if((Get-FileHash -LiteralPath $entry.dll -Algorithm SHA256).Hash -ne $entry.sha256){throw 'Legacy DLL checksum mismatch'}}
try{
    foreach($arch in @('x86','x64')){Invoke-Registrar $state.active $arch 'unregister'}
    foreach($entry in $legacy){Invoke-LegacyRegistrar $state.active $entry 'register'}
    $run='HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'
    if($null -ne $recovery.autostart){Set-ItemProperty -LiteralPath $run -Name RimesBroker -Value $recovery.autostart}
    else{Remove-ItemProperty -LiteralPath $run -Name RimesBroker -ErrorAction SilentlyContinue}
    Move-Item -LiteralPath "$InstallRoot\state.json" -Destination "$InstallRoot\legacy-restored-state.json" -Force
}catch{
    foreach($entry in $legacy){try{Invoke-LegacyRegistrar $state.active $entry 'unregister'}catch{Write-Warning $_}}
    foreach($arch in @('x64','x86')){Invoke-Registrar $state.active $arch 'register'}
    throw 'Legacy rollback failed; the preview registration was restored.'
}
Write-Output 'Restored the exact previous DLL paths and startup setting. User data retained. Sign out before daily use if any host used the preview.'
