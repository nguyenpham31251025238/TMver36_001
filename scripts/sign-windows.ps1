param(
    [string[]]$FilesToSign = @(),
    [string]$CertOutputDir = ""
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($CertOutputDir)) {
    $CertOutputDir = Join-Path $ProjectRoot "dist"
}
New-Item -ItemType Directory -Force -Path $CertOutputDir | Out-Null

$subject = "CN=TeacherMatch Desktop Publisher, O=TeacherMatch, C=VN"
$cert = Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert | Where-Object { $_.Subject -eq $subject -and $_.NotAfter -gt (Get-Date) } | Select-Object -First 1

if (-not $cert) {
    Write-Host "Creating self-signed Code Signing Certificate ($subject)..."
    $cert = New-SelfSignedCertificate `
        -Type CodeSigningCert `
        -Subject $subject `
        -KeyAlgorithm RSA `
        -KeyLength 2048 `
        -HashAlgorithm SHA256 `
        -CertStoreLocation "Cert:\CurrentUser\My" `
        -NotAfter (Get-Date).AddYears(3)
}

$cerPath = Join-Path $CertOutputDir "TeacherMatch-CodeSigning.cer"
Export-Certificate -Cert $cert -FilePath $cerPath -Force | Out-Null
Write-Host "Exported public certificate to: $cerPath"

# Also add certificate to CurrentUser Root & TrustedPublisher stores so local verification is Valid
certutil -user -addstore Root $cerPath >$null 2>&1
certutil -user -addstore TrustedPublisher $cerPath >$null 2>&1

foreach ($file in $FilesToSign) {
    if (Test-Path $file) {
        Set-ItemProperty -Path $file -Name IsReadOnly -Value $false -ErrorAction SilentlyContinue
        attrib -R $file >$null 2>&1
        Write-Host "Signing $file ..."
        $sig = Set-AuthenticodeSignature -FilePath $file -Certificate $cert -HashAlgorithm SHA256
        Write-Host "  Status: $($sig.Status) ($($sig.StatusMessage))"
    }
}
