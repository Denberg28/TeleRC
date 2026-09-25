param(
    [switch]$InstallSdkPackages,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$javaInfo = (& cmd.exe /d /s /c 'java -version 2>&1' | Select-Object -First 1).ToString()
if ($LASTEXITCODE -ne 0 -or $javaInfo -notmatch '(?:version\s+"17(?:\.|"))|(?:openjdk\s+17(?:\.|\s))') {
    throw "JDK 17 required. Detected: $javaInfo. Install JDK 17 and set JAVA_HOME."
}

$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $sdkRoot) { $sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path $sdkRoot -PathType Container)) {
    throw "Android SDK missing at $sdkRoot. Install Android Studio SDK tools or set ANDROID_HOME."
}
$env:ANDROID_HOME = (Resolve-Path $sdkRoot).Path
$sdkManager = Join-Path $env:ANDROID_HOME 'cmdline-tools\latest\bin\sdkmanager.bat'
$platformJar = Join-Path $env:ANDROID_HOME 'platforms\android-35\android.jar'
$buildTools = Join-Path $env:ANDROID_HOME 'build-tools\35.0.0'

if ($InstallSdkPackages) {
    if (-not (Test-Path $sdkManager)) { throw "Install Android SDK Command-line Tools (latest) first: $sdkManager" }
    & $sdkManager "--sdk_root=$env:ANDROID_HOME" 'platforms;android-35' 'build-tools;35.0.0'
    if ($LASTEXITCODE -ne 0) { throw 'Android SDK package installation failed.' }
}
if (-not (Test-Path $platformJar) -or -not (Test-Path $buildTools -PathType Container)) {
    throw 'SDK 35 or Build Tools 35.0.0 missing. Run this script with -InstallSdkPackages.'
}

$wrapper = Join-Path $projectRoot 'gradlew.bat'
$wrapperJar = Join-Path $projectRoot 'gradle\wrapper\gradle-wrapper.jar'
if (-not (Test-Path $wrapper) -or -not (Test-Path $wrapperJar)) {
    throw 'The checked-in Gradle wrapper is missing. Restore gradlew.bat and gradle/wrapper from Git.'
}
Write-Host "Using Java 17 and Android SDK $env:ANDROID_HOME"
Push-Location $projectRoot
try {
    & $wrapper --version
    if ($LASTEXITCODE -ne 0) {
        throw 'Gradle distribution download failed. Permit services.gradle.org and its GitHub release redirect, then retry.'
    }
    if (-not $SkipBuild) {
        & $wrapper :app:testDebugUnitTest :app:assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Android build or unit tests failed; inspect the Gradle output above.' }
        Write-Host 'Debug APK: app\build\outputs\apk\debug\app-debug.apk'
    }
} finally {
    Pop-Location
}
