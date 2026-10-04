#requires -Version 5.1
[CmdletBinding()]
param([ValidateSet('Verify','Install')][string]$Action='Verify',[switch]$NoAutostart)
. "$PSScriptRoot\Package.Common.ps1"
$manifest=Read-VerifiedPackage $PSScriptRoot
if($Action -eq 'Verify'){
    [ordered]@{verified=$true;installed=$false;version=$manifest.version;commit=$manifest.commit;signing=$manifest.signing.mode;files=$manifest.files.Count} | ConvertTo-Json -Compress
    return
}
Assert-Administrator
# The EXE carries the official installers so a clean Windows machine needs no
# compiler, Python, manually opened shell, or separate runtime download.
$restart=$false
foreach($arch in @('x64','x86')){
    $runtime=Join-Path $PSScriptRoot "runtimes\vc_redist.$arch.exe"
    $signature=Get-AuthenticodeSignature -LiteralPath $runtime
    if($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'O=Microsoft Corporation(?:,|$)'){
        throw "Visual C++ runtime signature validation failed: $arch"
    }
    $process=Start-Process -FilePath $runtime -ArgumentList @('/install','/quiet','/norestart') -Wait -PassThru
    # 1638 means a newer redistributable is already installed.
    if($process.ExitCode -notin @(0,3010,1638)){throw "Visual C++ runtime installation failed: $arch, exit $($process.ExitCode)"}
    if($process.ExitCode -eq 3010){$restart=$true}
}
& "$PSScriptRoot\Install.ps1" -NoAutostart:$NoAutostart -AllowPendingRestart | Out-Host
$verification=& "$PSScriptRoot\Verify.ps1"
if(-not $verification.Verified -or $verification.Version -ne $manifest.version){throw 'Installed version verification failed'}
[ordered]@{verified=$true;installed=$true;version=$manifest.version;requiresSignOut=[bool]$verification.RequiresSignOut;requiresRestart=$restart;directory=$verification.Directory} | ConvertTo-Json -Compress
