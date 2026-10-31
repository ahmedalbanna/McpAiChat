# Builds (and optionally installs) the debug APK.
#
#   tools\build.ps1            -> assembleDebug
#   tools\build.ps1 -Install   -> assembleDebug + adb install
#   tools\build.ps1 -Errors    -> show every compiler error, not just a summary
param(
    [switch]$Install,
    [switch]$Errors
)

# Gradle writes progress to stderr, so it is captured as text rather than
# treated as a terminating error by PowerShell.
$ErrorActionPreference = "Continue"

Set-Location $PSScriptRoot\..

$jbr   = "C:\Program Files\Android\Android Studio\jbr"
$adb   = "H:\Sdk\platform-tools\adb.exe"
$apk   = "app\build\outputs\apk\debug\app-debug.apk"

$env:JAVA_HOME    = $jbr
$env:ANDROID_HOME = "H:\Sdk"
$env:PATH         = "$jbr\bin;$env:PATH"

Write-Host "==> gradle :app:assembleDebug"
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$log = New-TemporaryFile
& cmd /c "gradle :app:assembleDebug --no-daemon > `"$log`" 2>&1"
$rc = $LASTEXITCODE
$sw.Stop()
Write-Host ("    finished in {0:n1}s (exit {1})" -f $sw.Elapsed.TotalSeconds, $rc)

$text = Get-Content $log -Raw
Remove-Item $log -Force

$problems = ($text -split "`r?`n") |
    Where-Object { $_ -match '^e: ' -or $_ -match 'error:' -or $_ -match 'FAILED' }

if ($problems) {
    Write-Host "`n-- problems --" -ForegroundColor Red
    $problems | ForEach-Object { Write-Host $_ }
    Write-Host "`nBUILD FAILED" -ForegroundColor Red
    exit 1
}

if ($text -match 'BUILD SUCCESSFUL') {
    Write-Host "BUILD SUCCESSFUL" -ForegroundColor Green
} else {
    Write-Host "BUILD FAILED (see gradle output above)" -ForegroundColor Red
    exit 1
}

if ($Install) {
    if (-not (Test-Path $apk)) {
        Write-Host "APK not found: $apk" -ForegroundColor Red
        exit 1
    }
    Write-Host "`n==> installing $apk"
    & $adb install -r $apk
    if ($LASTEXITCODE -ne 0) {
        Write-Host "    adb install failed (exit $LASTEXITCODE)" -ForegroundColor Red
        exit 1
    }
    $size = [math]::Round((Get-Item $apk).Length / 1MB, 2)
    Write-Host ("    {0} MB installed" -f $size) -ForegroundColor Green
}
