$adb = "H:/Sdk/platform-tools/adb.exe"
Write-Host "Opening Settings..."
& $adb shell am start -n com.mcpai.chat/.ui.SettingsActivity > $null 2>&1
Start-Sleep -Seconds 6
cmd /c "$adb exec-out screencap -p > C:\Users\ahmed\McpAiChat\docs\52-settings-enc.png"
Write-Host "Done."
