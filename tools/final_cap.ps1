# Taps Allow on the tool-approval dialog, then captures the result screen.
$adb = "H:\Sdk\platform-tools\adb.exe"
Write-Host "tapping Allow..."
& $adb shell input tap 598 869
Start-Sleep -Seconds 35
Write-Host "capturing result with cap.bat..."
& cmd /c "C:\Users\ahmed\McpAiChat\tools\cap.bat C:\Users\ahmed\McpAiChat\docs\47-result.png"
