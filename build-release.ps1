# Builds the three apps of the share edition into release\ : MakkahTowerHome.apk, SalahTimes.apk, Weather.apk.
# Uses the Android SDK and Java set up for CleanYourTV on this PC.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$main = Join-Path (Split-Path -Parent $root) 'CleanYourTV\cleanyourtv'
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
$env:Path = "$env:JAVA_HOME\bin;" + [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
New-Item -ItemType Directory -Force -Path "$root\release" | Out-Null
# The SDK location and the Fontshare fonts come from the main CleanYourTV folder on this PC.
foreach ($m in 'launcher', 'addons\prayer-times', 'addons\weather') {
    if (Test-Path "$main\$m\local.properties") { Copy-Item "$main\$m\local.properties" "$root\$m\local.properties" -Force }
}
foreach ($f in 'clash_display_bold.otf', 'cabinet_grotesk_bold.otf', 'cabinet_grotesk_medium.otf') {
    $dst = "$root\launcher\app\src\main\res\font\$f"
    if (-not (Test-Path $dst)) { Copy-Item "$main\launcher\app\src\main\res\font\$f" $dst }
}
$apps = [ordered]@{ 'launcher' = 'MakkahTowerHome.apk'; 'addons\prayer-times' = 'SalahTimes.apk'; 'addons\weather' = 'Weather.apk' }
foreach ($m in $apps.Keys) {
    Write-Host "== Building $($apps[$m])" -ForegroundColor Cyan
    Push-Location "$root\$m"
    $ErrorActionPreference = 'Continue'
    & .\gradlew.bat --stop 2>&1 | Out-Null
    Remove-Item 'app\build' -Recurse -Force -ErrorAction SilentlyContinue
    & .\gradlew.bat assembleRelease -q --console=plain
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    $apk = Get-ChildItem 'app\build\outputs\apk\release' -Filter *.apk -ErrorAction SilentlyContinue | Select-Object -First 1
    Pop-Location
    if ($code -ne 0 -or -not $apk) { throw "$($apps[$m]) did not build" }
    Copy-Item $apk.FullName "$root\release\$($apps[$m])" -Force
}
Get-ChildItem "$root\release" | ForEach-Object { Write-Host ("  {0}  {1:N1} MB" -f $_.Name, ($_.Length / 1MB)) }
Write-Host 'Built. Next: publish-github.ps1' -ForegroundColor Green
