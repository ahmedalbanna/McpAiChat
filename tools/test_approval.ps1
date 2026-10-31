# Verifies the tool-call approval prompt:
#   1. launch against the local MCP server + mock provider
#   2. send a message the mock answers with a tool call
#   3. capture the approval dialog
#   4. deny it, and confirm the card shows "declined"
param(
    # "allow" runs the tool; "deny" refuses it.
    [ValidateSet("allow", "deny")]
    [string]$Choice = "deny"
)

$adb = "H:\Sdk\platform-tools\adb.exe"
$docs = "C:\Users\ahmed\McpAiChat\docs"

function Capture($name) {
    $path = Join-Path $docs "$name.png"
    & cmd /c "$adb exec-out screencap -p > `"$path`""
    $kb = [math]::Round((Get-Item $path).Length / 1KB, 1)
    Write-Host ("  -> {0}.png ({1} KB)" -f $name, $kb)
}

$mcpPort = 8099
$providerPort = 8098

Write-Host "==> clean start"
& $adb shell pm clear com.mcpai.chat | Out-Null
& $adb shell am force-stop com.mcpai.chat | Out-Null
& $adb shell am start -n com.mcpai.chat/.ui.MainActivity `
    --es api_key "sk-test-local-test" `
    --es base_url "http://127.0.0.1:$providerPort/v1" `
    --es model "mock-model" `
    --es mcp_url "http://127.0.0.1:$mcpPort/mcp" | Out-Null
Start-Sleep -Seconds 10
Capture "32-connected"

Write-Host "==> sending a message that triggers a tool call"
& $adb shell input tap 280 1224 | Out-Null
Start-Sleep -Seconds 2
& $adb shell input text "use%sthe%sadd%stool%sfor%s20%splus%s22" | Out-Null
Start-Sleep -Seconds 2
& $adb shell input tap 640 715 | Out-Null
Start-Sleep -Seconds 14
Capture "33-approval-dialog"

# Measured from tools\find_button.ps1 on a 720x1280 device:
#   Deny  -> center (488, 869)
#   Allow -> center (598, 869)
if ($Choice -eq "allow") {
    Write-Host "==> tapping Allow"
    & $adb shell input tap 598 869 | Out-Null
} else {
    Write-Host "==> tapping Deny"
    & $adb shell input tap 488 869 | Out-Null
}
Start-Sleep -Seconds 10
Capture "34-after-decision"

& $adb shell input keyevent 111 | Out-Null
Start-Sleep -Seconds 2
Capture "35-final"

Write-Host "`n==> app still alive?"
$focus = & $adb shell dumpsys window 2>$null | Select-String "mCurrentFocus"
Write-Host "  $focus"
$crashes = & $adb logcat -d 2>$null | Select-String "FATAL EXCEPTION" | Select-Object -Last 2
if ($crashes) { Write-Host $crashes } else { Write-Host "  no crash" -ForegroundColor Green }
