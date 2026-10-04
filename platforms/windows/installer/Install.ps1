#requires -Version 5.1
[CmdletBinding()]
param([string]$InstallRoot = "$env:ProgramFiles\RIMES",[switch]$NoAutostart,[switch]$AllowPendingRestart)
. "$PSScriptRoot\Package.Common.ps1"
Assert-Administrator
$manifest=Read-VerifiedPackage $PSScriptRoot
$InstallRoot=[IO.Path]::GetFullPath($InstallRoot)
$packageHash=(Get-FileHash -LiteralPath "$PSScriptRoot\PACKAGE.json" -Algorithm SHA256).Hash.ToLowerInvariant()
$target=Join-Path $InstallRoot ('versions\'+$manifest.version+'-'+$manifest.commit.Substring(0,12)+'-'+$packageHash.Substring(0,12))
$previous=$null
$legacy=@()
$requiresRestart=$false
$runKey='HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'
$oldAutostart=$null
$runHandle=[Microsoft.Win32.Registry]::CurrentUser.OpenSubKey('Software\Microsoft\Windows\CurrentVersion\Run')
if($runHandle){$oldAutostart=$runHandle.GetValue('RimesBroker',$null);$runHandle.Dispose()}
if (Test-Path -LiteralPath "$InstallRoot\state.json") {
    $previous=Get-Content -LiteralPath "$InstallRoot\state.json" -Raw | ConvertFrom-Json
    $requiresRestart=[bool]$previous.requiresSignOut
    Assert-OwnedVersion $InstallRoot $previous.active | Out-Null
    if ($previous.active -eq $target) { & "$target\Verify.ps1" -InstallRoot $InstallRoot; return }
    Stop-OwnedBroker $previous.active
    try{Assert-Unlocked $previous.active}catch{if(-not $AllowPendingRestart){throw};$requiresRestart=$true}
}
if(-not $previous){
    $legacy=@(Get-LegacyViews)
    foreach($entry in $legacy){
        try{$stream=[IO.File]::Open($entry.dll,'Open','ReadWrite','None');$stream.Dispose()}
        catch{if(-not $AllowPendingRestart){throw 'Existing RIMES is loaded. Sign out first or explicitly use -AllowPendingRestart with immutable version directories.'};$requiresRestart=$true}
    }
}
if (Test-Path -LiteralPath $target) { Read-VerifiedPackage $target | Out-Null }
else {
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    Copy-Item -Path "$PSScriptRoot\*" -Destination $target -Recurse
    Read-VerifiedPackage $target | Out-Null
}
# Dependency and architecture probes precede every registration mutation.
foreach ($arch in @('x64','x86')) { & "$target\$arch\RimesRegistrar.exe" metadata; if($LASTEXITCODE){throw 'Registrar dependency preflight failed'} }
& "$target\x64\RimesBroker.exe" --print-paths
if($LASTEXITCODE){throw 'Broker dependency preflight failed'}
& "$target\x64\RimesBroker.exe" --deploy-only
if($LASTEXITCODE){throw 'Dictionary deployment failed before registration; prior installation retained'}
$registered=@()
if($legacy.Count){[ordered]@{entries=$legacy;autostart=$oldAutostart;recordedAt=(Get-Date).ToString('o')} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$InstallRoot\legacy-recovery.json" -Encoding UTF8}
try {
    if($previous){ foreach($arch in @('x86','x64')) {Invoke-Registrar $previous.active $arch 'unregister'} }
    else{foreach($entry in $legacy){Invoke-LegacyRegistrar $target $entry 'unregister'}}
    foreach($arch in @('x64','x86')) {Invoke-Registrar $target $arch 'register'; $registered+=$arch}
    foreach($arch in @('x64','x86')) {Invoke-Registrar $target $arch 'verify'}
    if(-not $NoAutostart){ & "$target\x64\RimesBroker.exe" --install-autostart; if($LASTEXITCODE){throw 'Autostart registration failed'} }
    else{Remove-ItemProperty -LiteralPath $runKey -Name RimesBroker -ErrorAction SilentlyContinue}
    $oldPath=if($previous){$previous.active}else{''}
    Write-InstallState $InstallRoot ([ordered]@{active=$target;previous=$oldPath;version=$manifest.version;commit=$manifest.commit;requiresSignOut=$requiresRestart;legacy=$legacy;previousAutostart=$oldAutostart;installedAt=(Get-Date).ToString('o')})
} catch {
    $failure=$_
    $rollbackFailures=@()
    foreach($arch in $registered){try{Invoke-Registrar $target $arch 'unregister'}catch{$rollbackFailures+=$_.ToString()}}
    if($previous){foreach($arch in @('x64','x86')){try{Invoke-Registrar $previous.active $arch 'register'}catch{$rollbackFailures+=$_.ToString()}}}
    else{foreach($entry in $legacy){try{Invoke-LegacyRegistrar $target $entry 'register'}catch{$rollbackFailures+=$_.ToString()}}}
    try{if($null -ne $oldAutostart){Set-ItemProperty -LiteralPath $runKey -Name RimesBroker -Value $oldAutostart}else{Remove-ItemProperty -LiteralPath $runKey -Name RimesBroker -ErrorAction SilentlyContinue}}catch{$rollbackFailures+=$_.ToString()}
    if($rollbackFailures.Count){throw "Installation failed: $failure. Recovery is incomplete: $($rollbackFailures -join '; '). Recovery records and all prior DLLs are retained."}
    throw "Installation failed; prior registration and startup setting restored. $failure"
}
if($requiresRestart){Write-Output 'Registered the new immutable version. SIGN-OUT REQUIRED before daily use; running hosts may still use the previous DLL. No old DLL was overwritten.'}
else{Write-Output 'Installed and verified both TSF architectures. Select RIMES with Win+Space. User data was retained.'}
