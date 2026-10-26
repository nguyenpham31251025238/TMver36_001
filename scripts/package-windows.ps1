param(
    [ValidateSet("msi", "exe", "app-image", "all")]
    [string]$InstallerType = "all",
    [string]$AppVersion = "1.0.0",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

$candidateJdk = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
if (Test-Path (Join-Path $candidateJdk "bin\jpackage.exe")) {
    $env:JAVA_HOME = $candidateJdk
} elseif (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\jpackage.exe"))) {
    throw "JAVA_HOME with JDK 21+ (containing bin\jpackage.exe) is required."
}
$env:PATH = "$($env:JAVA_HOME)\bin;$($env:PATH)"

$mvnCmd = "mvn"
if (-not (Get-Command $mvnCmd -ErrorAction SilentlyContinue)) {
    $fallbackMvn = "C:\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd"
    if (Test-Path $fallbackMvn) {
        $mvnCmd = $fallbackMvn
    } else {
        throw "Maven (mvn) was not found on PATH."
    }
}

# Ensure WiX Toolset v3 (candle.exe / light.exe) is on PATH for .msi / .exe packaging
if (-not (Get-Command "candle.exe" -ErrorAction SilentlyContinue)) {
    $wixDir = Join-Path $env:TEMP "wix311-portable"
    $candlePath = Join-Path $wixDir "candle.exe"
    if (-not (Test-Path $candlePath)) {
        Write-Host "Downloading portable WiX Toolset 3.11.2 binaries..."
        New-Item -ItemType Directory -Force -Path $wixDir | Out-Null
        $wixZip = Join-Path $env:TEMP "wix311-binaries.zip"
        Invoke-WebRequest -Uri "https://github.com/wixtoolset/wix3/releases/download/wix3112rtm/wix311-binaries.zip" -OutFile $wixZip
        Expand-Archive -Path $wixZip -DestinationPath $wixDir -Force
    }
    $env:PATH = "$wixDir;$($env:PATH)"
}

Write-Host "Building application and copying runtime dependencies with Maven..."
if ($SkipTests) {
    & $mvnCmd compile package -DskipTests
} else {
    & $mvnCmd test-compile test package
}
if ($LASTEXITCODE -ne 0) {
    throw "Maven package failed with exit code $LASTEXITCODE"
}

$inputDir = Join-Path $ProjectRoot "target\jpackage-input"
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
Copy-Item -Path (Join-Path $ProjectRoot "target\TMver36-1.0-SNAPSHOT.jar") -Destination $inputDir -Force

$distDir = Join-Path $ProjectRoot "dist"
New-Item -ItemType Directory -Force -Path $distDir | Out-Null

$jpackageExe = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
$signScript = Join-Path $PSScriptRoot "sign-windows.ps1"

# 1. Build self-contained app-image first (used both for Portable ZIP and to sign TeacherMatch.exe)
$appImageRoot = Join-Path $env:TEMP "tmver36-app-image"
if (Test-Path $appImageRoot) {
    Remove-Item $appImageRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $appImageRoot | Out-Null

Write-Host "Running jpackage for self-contained app-image..."
& $jpackageExe `
    --type app-image `
    --name "TeacherMatch" `
    --app-version $AppVersion `
    --vendor "TeacherMatch" `
    --description "TeacherMatch Desktop Application (TMver36)" `
    --dest $appImageRoot `
    --input $inputDir `
    --main-jar "TMver36-1.0-SNAPSHOT.jar" `
    --main-class "com.example.tmver36.Launcher" `
    --add-modules "java.base,java.desktop,java.xml,java.logging,java.scripting,java.naming,java.prefs,java.datatransfer,java.net.http,jdk.crypto.ec,jdk.unsupported"
if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed for app-image with exit code $LASTEXITCODE"
}

$appImageDir = Join-Path $appImageRoot "TeacherMatch"
$appExe = Join-Path $appImageDir "TeacherMatch.exe"

# Create 1-click launcher and README inside the portable folder
$runBat = "@echo off`r`ncd /d `"%~dp0`"`r`nstart `"`" `"%~dp0TeacherMatch.exe`"`r`n"
Set-Content -Path (Join-Path $appImageDir "Run-TeacherMatch.bat") -Value $runBat -Encoding ASCII

# Sign TeacherMatch.exe inside app-image before zipping and packaging
& $signScript -FilesToSign @($appExe) -CertOutputDir $distDir

# Create Portable ZIP (TeacherMatch-1.0.0-win64-portable.zip)
$portableZip = Join-Path $distDir "TeacherMatch-$AppVersion-win64-portable.zip"
if (Test-Path $portableZip) {
    Remove-Item $portableZip -Force
}
Write-Host "Creating Portable ZIP: $portableZip ..."
Compress-Archive -Path $appImageDir -DestinationPath $portableZip -Force

# 2. Build .msi and .exe installers from the signed app-image
$installerTypes = switch ($InstallerType) {
    "all"       { @("msi", "exe") }
    "app-image" { @() }
    default     { @($InstallerType) }
}

foreach ($type in $installerTypes) {
    Write-Host "Running jpackage for installer type: $type ..."
    $existing = Join-Path $distDir "TeacherMatch-$AppVersion.$type"
    if (Test-Path $existing) {
        Remove-Item $existing -Force
    }
    & $jpackageExe `
        --type $type `
        --name "TeacherMatch" `
        --app-version $AppVersion `
        --vendor "TeacherMatch" `
        --description "TeacherMatch Desktop Application (TMver36)" `
        --dest $distDir `
        --app-image $appImageDir `
        --win-per-user-install `
        --win-dir-chooser `
        --win-menu `
        --win-menu-group "TeacherMatch" `
        --win-shortcut
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed for type $type with exit code $LASTEXITCODE"
    }
    & $signScript -FilesToSign @($existing) -CertOutputDir $distDir
}

# Create 1-click Install-TeacherMatch.bat in dist/ that imports the certificate for the current user and launches the MSI
$installBat = @"
@echo off
setlocal
cd /d "%~dp0"
if exist "%~dp0TeacherMatch-CodeSigning.cer" (
    certutil -user -addstore Root "%~dp0TeacherMatch-CodeSigning.cer" >nul 2>&1
    certutil -user -addstore TrustedPublisher "%~dp0TeacherMatch-CodeSigning.cer" >nul 2>&1
)
if exist "%~dp0TeacherMatch-$AppVersion.msi" (
    echo Installing TeacherMatch $AppVersion ...
    msiexec /i "%~dp0TeacherMatch-$AppVersion.msi"
) else if exist "%~dp0TeacherMatch-$AppVersion.exe" (
    start "" "%~dp0TeacherMatch-$AppVersion.exe"
)
"@
Set-Content -Path (Join-Path $distDir "Install-TeacherMatch.bat") -Value $installBat -Encoding ASCII

Write-Host "Windows distributables created and signed in ${distDir}:"
Get-ChildItem -Path $distDir | Select-Object Name, Length, LastWriteTime
