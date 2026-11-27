# SkimRunner - make the APK signing key ONCE.
# Run in PowerShell:  powershell -ExecutionPolicy Bypass -File .\make-keystore.ps1
# It creates a folder "skimrunner-signing" in your Documents with:
#   skimrunner-release.p12       the signing key (BACK THIS UP - lose it and the app can never be updated)
#   KEYSTORE_BASE64.txt          paste into GitHub secret KEYSTORE_BASE64
#   KEYSTORE_PASSWORD.txt        paste into GitHub secret KEYSTORE_PASSWORD
# Never upload these files to the GitHub repo itself.
param([string]$OutDir = (Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'skimrunner-signing'))

$p12 = Join-Path $OutDir 'skimrunner-release.p12'
if (Test-Path $p12) {
    Write-Host "A key already exists at $p12 - not making a new one (a new key would break updates)." -ForegroundColor Yellow
    exit 1
}
New-Item -ItemType Directory -Force $OutDir | Out-Null

# Random 24-character password
$chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'.ToCharArray()
$bytes = New-Object byte[] 24
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$pass = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })

$cert = New-SelfSignedCertificate -Subject 'CN=SkimRunner, O=markdave' -FriendlyName 'skimrunner' `
    -KeyAlgorithm RSA -KeyLength 2048 -HashAlgorithm SHA256 -KeyExportPolicy Exportable `
    -KeyUsage DigitalSignature -Type CodeSigningCert -NotAfter (Get-Date).AddYears(30) `
    -CertStoreLocation 'Cert:\CurrentUser\My'
try {
    $sec = ConvertTo-SecureString $pass -AsPlainText -Force
    Export-PfxCertificate -Cert $cert -FilePath $p12 -Password $sec | Out-Null
} finally {
    Remove-Item "Cert:\CurrentUser\My\$($cert.Thumbprint)" -DeleteKey -ErrorAction SilentlyContinue
}

[Convert]::ToBase64String([IO.File]::ReadAllBytes($p12)) | Set-Content -Encoding ascii -NoNewline (Join-Path $OutDir 'KEYSTORE_BASE64.txt')
$pass | Set-Content -Encoding ascii -NoNewline (Join-Path $OutDir 'KEYSTORE_PASSWORD.txt')

Write-Host ''
Write-Host "Done! Your signing files are in: $OutDir" -ForegroundColor Green
Write-Host '1. Copy that whole folder to a USB stick or OneDrive as a backup.'
Write-Host '2. Put KEYSTORE_BASE64.txt and KEYSTORE_PASSWORD.txt into GitHub Secrets (see README).'
