# Runs the unit tests.
#
#   tools\test.ps1                 -> all tests
#   tools\test.ps1 -Filter "TurnHistoryTest"
param([string]$Filter = "")

$ErrorActionPreference = "Continue"

Set-Location (Split-Path $PSScriptRoot -Parent)

$env:JAVA_HOME    = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "H:\Sdk"
$env:PATH         = "$env:JAVA_HOME\bin;$env:PATH"

$target = ":app:testDebugUnitTest"
if ($Filter) {
    # :app:testDebugUnitTest --tests "com.mcpai.chat.data.TurnHistoryTest"
    $target = $target
    $gradleArgs = @(":app:testDebugUnitTest", "--tests", $Filter, "--no-daemon")
} else {
    $gradleArgs = @(":app:testDebugUnitTest", "--no-daemon")
}

Write-Host ("==> gradle {0}" -f ($gradleArgs -join " "))
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$log = New-TemporaryFile
& cmd /c "gradle $($gradleArgs -join ' ') > `"$log`" 2>&1"
$rc = $LASTEXITCODE
$sw.Stop()
Write-Host ("    finished in {0:n1}s (exit {1})" -f $sw.Elapsed.TotalSeconds, $rc)

$text = Get-Content $log -Raw

$lines = ($text -split "`r?`n") | Where-Object { $_ -match 'PASSED|FAILED|tests completed|e: ' }
$lines | ForEach-Object { Write-Host $_ }

if ($text -match 'BUILD SUCCESSFUL') {
    Write-Host "`nTESTS PASSED" -ForegroundColor Green
} else {
    Write-Host "`nTESTS FAILED -- full log:" -ForegroundColor Red
    Write-Host $log
    exit 1
}
