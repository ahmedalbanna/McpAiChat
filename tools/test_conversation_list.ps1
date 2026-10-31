$adb = "H:/Sdk/platform-tools/adb.exe"
$docs = "C:\Users\ahmed\McpAiChat\docs"

function Capture($name) {
    $path = Join-Path $docs "$name.png"
    cmd /c "$adb exec-out screencap -p > `"$path`""
    $kb = [math]::Round((Get-Item $path).Length / 1KB, 1)
    Write-Host ("  -> {0}.png ({1} KB)" -f $name, $kb)
}

Write-Host "==> launch app (conversation list)"
& $adb shell am force-stop com.mcpai.chat | Out-Null
& $adb shell am start -n com.mcpai.chat/.ui.ConversationListActivity > $null 2>&1
Start-Sleep -Seconds 6
Capture "61-conversation-list-empty"

Write-Host "==> tap new chat FAB"
& $adb shell input tap 880 1220 > $null 2>&1
Start-Sleep -Seconds 4
Capture "62-chat-opened"

Write-Host "==> type a message to set title"
& $adb shell input tap 280 1224 > $null 2>&1
Start-Sleep -Seconds 2
& $adb shell input text "Hello world test message" > $null 2>&1
Start-Sleep -Seconds 2
& $adb shell input tap 880 1224 > $null 2>&1
Start-Sleep -Seconds 6
Capture "63-message-sent"

Write-Host "==> go back to conversation list"
& $adb shell input keyevent 4 > $null 2>&1
Start-Sleep -Seconds 2
Capture "64-back-to-list"
