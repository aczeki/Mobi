param([switch]$Test, [switch]$DeviceTest)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $env:JAVA_HOME -and (Test-Path 'C:\Program Files\Java\jdk-17')) { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if ($Test) { & .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon }
else { & .\gradlew.bat assembleDebug --no-daemon }
if ($LASTEXITCODE -ne 0) { throw 'Budowanie Androida nie powiodło się.' }
if ($DeviceTest) {
    & .\gradlew.bat assembleDebugAndroidTest --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Budowanie pakietu testowego nie powiodło się.' }
    $mobiAdb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
    $mobiDevices = & $mobiAdb devices
    $mobiEmulator = $mobiDevices | Where-Object { $_ -match '^emulator-\d+\s+device$' } | Select-Object -First 1
    if (-not $mobiEmulator) { throw 'Uruchom emulator Androida. Testy modyfikują dane aplikacji i nie są automatycznie uruchamiane na telefonie.' }
    $mobiSerial = ($mobiEmulator -split '\s+')[0]
    & $mobiAdb -s $mobiSerial install -r 'app\build\outputs\apk\debug\app-debug.apk'
    if ($LASTEXITCODE -ne 0) { throw 'Nie udało się zainstalować aplikacji na emulatorze.' }
    & $mobiAdb -s $mobiSerial install -r 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    if ($LASTEXITCODE -ne 0) { throw 'Nie udało się zainstalować pakietu testów.' }
    $mobiTestOutput = & $mobiAdb -s $mobiSerial shell am instrument -w -r pl.mobi.chat.test/androidx.test.runner.AndroidJUnitRunner
    $mobiTestOutput | Write-Output
    if ($LASTEXITCODE -ne 0 -or ($mobiTestOutput -join "`n") -notmatch 'OK \(\d+ tests?\)') { throw 'Testy na emulatorze nie powiodły się.' }
}
New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot 'dist') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk') -Destination (Join-Path $PSScriptRoot 'dist\Mobi.apk')
Write-Output 'Gotowe: dist\Mobi.apk'
