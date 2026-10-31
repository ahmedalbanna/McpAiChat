# Final end-to-end verification:
#   1. Launch against hcnsec.cn + DeepWiki catalog server
#   2. Send a tool-calling message
#   3. Tap Allow
#   4. Capture the result
param()

$adb = "H:\Sdk\platform-tools\adb.exe"
$docs = "C:\Users\ahmed\McpAiChat\docs"
# The provider API key comes from the environment, never from the repository.
$key = $env:MCPAICHAT_API_KEY
if (-not $key) { throw "Set MCPAICHAT_API_KEY before running this script." }

function Capture($name) {
    $path = Join-Path $docs "$name.png"
    & cmd /c "$adb exec-out screencap -p > `"$path`""
    $kb = [math]::Round((Get-Item $path).Length / 1KB, 1)
    Write-Host ("  -> {0}.png ({1} KB)" -f $name, $kb)
}

Write-Host "==> clean start + connect"
& $adb shell pm clear com.mcpai.chat | Out-Null
& $adb shell am force-stop com.mcpai.chat | Out-Null
& $adb shell am start -n com.mcpai.chat/.ui.MainActivity `
    --es api_key $key `
    --es base_url "https://api.hcnsec.cn/v1" `
    --es model "MiMo-V2.6-Flash" `
    --es mcp_url "https://mcp.deepwiki.com/mcp" | Out-Null
Start-Sleep -Seconds 10
Capture "39-final-connect"

Write-Host "==> servers screen shows DeepWiki connected"
& $adb shell input tap 505 96 | Out-Null
Start-Sleep -Seconds 4
Capture "40-final-servers"

& $adb shell input keyevent 4 | Out-Null
Start-Sleep -Seconds 2

Write-Host "==> trigger tool call against real provider"
& $adb shell input tap 280 1224 | Out-Null
Start-Sleep -Seconds 2
& $adb shell input text "ask%sdwikifor%ssquare%sfoundation%shistory" | Out-Null
Start-Sleep -Seconds 2
& $adb shell input tap 640 715 | Out-Null
Start-Sleep -Seconds 14
Capture "41-final-approval"

Write-Host "==> tap Allow"
& $adb shell input tap 598 869 | Out-Null
Start-Sleep -Seconds 30
Capture "42-final-answer"

Write-Host "==> app state"
$focus = & $adb shell dumpsys window 2>$null | Select-String "mCurrentFocus"
Write-Host "  $focus"
$crashes = & $adb logcat -d 2>$null | Select-String "FATAL EXCEPTION" | Select-Object -Last 2
if ($crashes) { Write-Host $crashes } else { Write-Host "  no crash" -ForegroundColor Green }
