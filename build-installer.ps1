# Builds the Windows downloads for Snake into dist\:
#   Snake-<version>.exe           one-click installer (bundles Java + database driver)
#   Snake-<version>-portable.zip  no-install version: unzip and run Snake.exe
#
# Players need neither Java nor WiX; both downloads are self-contained.
# This build machine only needs a JDK 17+. Everything else (the H2 database
# driver and the WiX Toolset used for the .exe installer) is downloaded
# automatically into %USERPROFILE%\tools, without admin rights. If WiX can't
# be obtained, the portable zip is still built.
#
# Usage:  powershell -ExecutionPolicy Bypass -File build-installer.ps1 [-Version 2.1.0]
param([string]$Version = "2.1.0")
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"   # makes Invoke-WebRequest much faster
Set-Location $PSScriptRoot

function Get-VerifiedDownload([string]$Url, [string]$OutFile, [string]$Algorithm, [string]$Hash) {
    if (-not (Test-Path $OutFile)) {
        Write-Host "Downloading $(Split-Path $Url -Leaf)..."
        New-Item -ItemType Directory -Force (Split-Path $OutFile) | Out-Null
        Invoke-WebRequest $Url -OutFile $OutFile -UseBasicParsing
    }
    if ((Get-FileHash $OutFile -Algorithm $Algorithm).Hash -ne $Hash.ToUpper()) {
        Remove-Item $OutFile
        throw "Checksum mismatch for $OutFile; deleted it. Re-run to download again."
    }
}

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "JDK 17+ not found. Install one (e.g. 'winget install EclipseAdoptium.Temurin.21.JDK') and re-run."
}

# --- Embedded database driver (H2) -------------------------------------------
$h2Version = "2.3.232"
$h2Jar = "lib\h2-$h2Version.jar"
Get-VerifiedDownload "https://repo1.maven.org/maven2/com/h2database/h2/$h2Version/h2-$h2Version.jar" `
    $h2Jar SHA1 "4fcc05d966ccdb2812ae8b9a718f69226c0cf4e2"

# --- WiX Toolset (only for the .exe installer) --------------------------------
$wixDir = Join-Path $env:USERPROFILE "tools\wix314"
$haveWix = [bool](Get-Command candle.exe -ErrorAction SilentlyContinue)
if (-not $haveWix) {
    if (-not (Test-Path "$wixDir\candle.exe")) {
        try {
            $zip = Join-Path $env:USERPROFILE "tools\wix314-binaries.zip"
            Get-VerifiedDownload "https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip" `
                $zip SHA256 "6ac824e1642d6f7277d0ed7ea09411a508f6116ba6fae0aa5f2c7daa2ff43d31"
            Expand-Archive $zip $wixDir -Force
            Remove-Item $zip
        } catch {
            Write-Warning "Could not get WiX ($($_.Exception.Message)). Only the portable zip will be built."
        }
    }
    if (Test-Path "$wixDir\candle.exe") {
        $env:PATH = "$wixDir;$env:PATH"
        $haveWix = $true
    }
}

# --- Compile and package the jar ----------------------------------------------
Write-Host "Compiling..."
Remove-Item -Recurse -Force out, build -ErrorAction SilentlyContinue
javac -encoding UTF-8 -d out (Get-ChildItem *.java, logic\*.java, graphics\*.java | ForEach-Object FullName)
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

New-Item -ItemType Directory -Force build\input, dist | Out-Null
Set-Content build\manifest.txt "Class-Path: h2-$h2Version.jar" -Encoding ascii
jar --create --file build\input\SnakeGame.jar --main-class SnakeGame --manifest build\manifest.txt -C out .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
Copy-Item $h2Jar build\input\

$common = @(
    "--name", "Snake",
    "--app-version", $Version,
    "--vendor", "Snake Game",
    "--description", "Snake - modern arcade edition",
    "--icon", "packaging\snake.ico",
    "--input", "build\input",
    "--main-jar", "SnakeGame.jar",
    "--main-class", "SnakeGame",
    "--add-modules", "java.desktop,java.sql,java.naming,java.management",
    "--jlink-options", "--strip-debug --no-header-files --no-man-pages --compress=zip-9"
)

# --- Portable zip (no installer, no WiX needed) -------------------------------
Write-Host "Building portable zip..."
jpackage --type app-image @common --dest build\image
if ($LASTEXITCODE -ne 0) { throw "jpackage (app-image) failed" }
$zipOut = "dist\Snake-$Version-portable.zip"
Remove-Item $zipOut -Force -ErrorAction SilentlyContinue
Compress-Archive build\image\Snake $zipOut

# --- One-click installer -------------------------------------------------------
if ($haveWix) {
    Write-Host "Building installer (this takes a minute)..."
    Remove-Item "dist\Snake-$Version.exe" -Force -ErrorAction SilentlyContinue  # WiX marks it read-only
    jpackage --type exe @common `
        --win-per-user-install `
        --win-dir-chooser `
        --win-menu `
        --win-menu-group "Games" `
        --win-shortcut `
        --win-upgrade-uuid "6f1c5d2e-8a43-4b7e-9c2f-5e3a1b7d9c40" `
        --dest dist
    if ($LASTEXITCODE -ne 0) { throw "jpackage (exe) failed" }
}

Remove-Item -Recurse -Force build
Get-ChildItem dist -Filter "Snake-$Version*" |
    Select-Object Name, @{n = 'SizeMB'; e = { [math]::Round($_.Length / 1MB, 1) } }
