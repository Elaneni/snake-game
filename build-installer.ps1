# Builds dist\Snake-<version>.exe: a single Windows installer that bundles its own
# Java runtime and the embedded H2 database driver, so the target PC needs nothing
# else installed. The game creates its database on first launch.
#
# Requirements on the build machine: JDK 17+ (jpackage) and WiX Toolset 3.x on PATH
# (or in %USERPROFILE%\tools\wix314).
#
# Usage:  powershell -ExecutionPolicy Bypass -File build-installer.ps1 [-Version 2.1.0]
param([string]$Version = "2.1.0")
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$wix = Join-Path $env:USERPROFILE "tools\wix314"
if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue) -and (Test-Path "$wix\candle.exe")) {
    $env:PATH = "$wix;$env:PATH"
}
if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    throw "WiX Toolset 3.x not found. Put its binaries on PATH or in $wix."
}

# Embedded database driver (H2). Downloaded once and checksum-verified.
$h2Version = "2.3.232"
$h2Sha1 = "4fcc05d966ccdb2812ae8b9a718f69226c0cf4e2"
$h2Jar = "lib\h2-$h2Version.jar"
if (-not (Test-Path $h2Jar)) {
    Write-Host "Downloading H2 database driver..."
    New-Item -ItemType Directory -Force lib | Out-Null
    $ProgressPreference = "SilentlyContinue"
    Invoke-WebRequest "https://repo1.maven.org/maven2/com/h2database/h2/$h2Version/h2-$h2Version.jar" -OutFile $h2Jar -UseBasicParsing
}
if ((Get-FileHash $h2Jar -Algorithm SHA1).Hash.ToLower() -ne $h2Sha1) {
    Remove-Item $h2Jar
    throw "H2 jar checksum mismatch; deleted it. Re-run to download again."
}

Write-Host "Compiling..."
Remove-Item -Recurse -Force out, build -ErrorAction SilentlyContinue
javac -encoding UTF-8 -d out (Get-ChildItem *.java, logic\*.java, graphics\*.java | ForEach-Object FullName)
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "Packaging jar..."
New-Item -ItemType Directory -Force build\input | Out-Null
Set-Content build\manifest.txt "Class-Path: h2-$h2Version.jar" -Encoding ascii
jar --create --file build\input\SnakeGame.jar --main-class SnakeGame --manifest build\manifest.txt -C out .
Copy-Item $h2Jar build\input\
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

Write-Host "Building installer (this takes a minute)..."
New-Item -ItemType Directory -Force dist | Out-Null
Remove-Item "dist\Snake-$Version.exe" -ErrorAction SilentlyContinue
jpackage `
    --type exe `
    --name Snake `
    --app-version $Version `
    --vendor "Snake Game" `
    --description "Snake - modern arcade edition" `
    --icon packaging\snake.ico `
    --input build\input `
    --main-jar SnakeGame.jar `
    --main-class SnakeGame `
    --add-modules java.desktop,java.sql,java.naming,java.management `
    --jlink-options "--strip-debug --no-header-files --no-man-pages --compress=zip-9" `
    --win-per-user-install `
    --win-dir-chooser `
    --win-menu `
    --win-menu-group "Games" `
    --win-shortcut `
    --win-upgrade-uuid "6f1c5d2e-8a43-4b7e-9c2f-5e3a1b7d9c40" `
    --dest dist
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

Remove-Item -Recurse -Force build
Get-Item "dist\Snake-$Version.exe" | Select-Object Name, @{n = 'SizeMB'; e = { [math]::Round($_.Length / 1MB, 1) } }
