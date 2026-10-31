$adb = "H:/Sdk/platform-tools/adb.exe"
Write-Host "Launching MainActivity..."
& $adb shell am start -n com.mcpai.chat/.ui.MainActivity > $null 2>&1
Start-Sleep -Seconds 8
Write-Host "Tapping Settings button..."
& $adb shell input tap 600 96 > $null 2>&1
Start-Sleep -Seconds 4
Write-Host "Capturing screenshot..."
cmd /c "$adb exec-out screencap -p > C:\Users\ahmed\McpAiChat\docs\53-settings-final.png"
Write-Host "Done."
