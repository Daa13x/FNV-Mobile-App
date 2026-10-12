# Run on a Windows PC with Android Studio / Android SDK and Java 17 installed.
# Read-only dev build. Does not deploy any game mod or alter saves.
$ErrorActionPreference = 'Stop'
$android = Join-Path $PSScriptRoot '..\android'
Push-Location $android
try {
    if (Test-Path '.\gradlew.bat') {
        $runner = '.\gradlew.bat'
    } elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
        $runner = 'gradle'
    } else {
        throw 'Gradle unavailable. Install Android Studio/Gradle and generate the standard Gradle wrapper; this archive intentionally does not include a fabricated wrapper.'
    }
    & $runner ':app:testDebugUnitTest' ':app:assembleDebug'
    if ($LASTEXITCODE -ne 0) { throw "Android build/tests failed with exit code $LASTEXITCODE" }
    $apk = Join-Path $android 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $apk)) { throw 'Gradle reported success but debug APK was not found' }
    Write-Host "Verified APK: $apk"
} finally { Pop-Location }
