$adb = "H:/Sdk/platform-tools/adb.exe"
$docs = "C:\Users\ahmed\McpAiChat\docs"
# The provider API key comes from the environment, never from the repository.
$key = $env:MCPAICHAT_API_KEY
if (-not $key) { throw "Set MCPAICHAT_API_KEY before running this script." }

function Capture($name) {
    $path = Join-Path $docs "$name.png"
    cmd /c "$adb exec-out screencap -p > `"$path`""
    $kb = [math]::Round((Get-Item $path).Length / 1KB, 1)
    Write-Host ("  -> {0}.png ({1} KB)" -f $name, $kb)
}

Write-Host "==> clean start + connect"
& $adb shell pm clear com.mcpai.chat | Out-Null
& $adb shell am force-stop com.mcpai.chat | Out-Null
& $adb shell am start -n com.mcpai.chat/.ui.MainActivity `
    --es api_key $key `
    --es base_url "https://api.hcnsec.cn/v1" `
    --es model "MiMo-V2.6-Flash" > $null 2>&1
Start-Sleep -Seconds 10
Capture "54-attach-connect"

Write-Host "==> tap attach button"
& $adb shell input tap 598 715 > $null 2>&1   # attach button
Start-Sleep -Seconds 3
Capture "55-attach-picker"
