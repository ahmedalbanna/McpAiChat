$adb = "H:/Sdk/platform-tools/adb.exe"
Write-Host "Opening SettingsActivity..."
& $adb shell am start -n com.mcpai.chat/.ui.SettingsActivity > $null 2>&1
Start-Sleep -Seconds 5
Write-Host "Capturing screenshot with cap.bat..."
cmd /c "C:\Users\ahmed\McpAiChat/tools\cap.bat C:\Users\ahmed\McpAiChat\docs/50-settings-open.png"
Write-Host "Done."
