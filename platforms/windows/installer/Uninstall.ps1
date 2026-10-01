#requires -Version 5.1
param([string]$InstallRoot="$env:ProgramFiles\RIMES")
. "$PSScriptRoot\Package.Common.ps1"
Assert-Administrator
$state=Get-Content -LiteralPath "$InstallRoot\state.json" -Raw | ConvertFrom-Json
Assert-OwnedVersion $InstallRoot $state.active | Out-Null
Stop-OwnedBroker $state.active
Assert-Unlocked $state.active
try {
    foreach($arch in @('x86','x64')){Invoke-Registrar $state.active $arch 'unregister'}
    & "$($state.active)\x64\RimesBroker.exe" --remove-autostart
    if($LASTEXITCODE){throw 'Could not remove autostart'}
    foreach($arch in @('x86','x64')){Invoke-Registrar $state.active $arch 'verify-absent'}
} catch {
    foreach($arch in @('x64','x86')){Invoke-Registrar $state.active $arch 'register'}
    throw 'Uninstall failed; registration restored. Installed files and user data retained.'
}
Move-Item -LiteralPath "$InstallRoot\state.json" -Destination "$InstallRoot\uninstalled-state.json" -Force
Write-Output 'Unregistered RIMES. Version files, user dictionaries, settings and credentials retained for recovery. No other input method was changed.'
