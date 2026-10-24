param(
    [ValidateSet("msi", "exe", "both")]
    [string]$InstallerType = "both",
    [string]$AppVersion = "1.0.0",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\jpackage.exe"))) {
    $candidateJdk = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
    if (Test-Path (Join-Path $candidateJdk "bin\jpackage.exe")) {
        $env:JAVA_HOME = $candidateJdk
    } else {
        throw "JAVA_HOME with JDK 21+ (containing bin\jpackage.exe) is required."
    }
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
    & $mvnCmd package -DskipTests
} else {
    & $mvnCmd package
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
$typesToBuild = if ($InstallerType -eq "both") { @("msi", "exe") } else { @($InstallerType) }

foreach ($type in $typesToBuild) {
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
        --input $inputDir `
        --main-jar "TMver36-1.0-SNAPSHOT.jar" `
        --main-class "com.example.tmver36.Launcher" `
        --add-modules "java.base,java.desktop,java.xml,java.logging,java.scripting,java.naming,java.prefs,java.datatransfer,jdk.unsupported" `
        --win-per-user-install `
        --win-dir-chooser `
        --win-menu `
        --win-menu-group "TeacherMatch" `
        --win-shortcut
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed for type $type with exit code $LASTEXITCODE"
    }
}

Write-Host "Windows installers created successfully in ${distDir}:"
Get-ChildItem -Path $distDir -Filter "TeacherMatch-*" | Select-Object Name, Length, LastWriteTime
