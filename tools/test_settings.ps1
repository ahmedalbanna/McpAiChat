# Opens the settings screen with the provider already configured, so the new
# controls (tool confirmation, history limit) render with real values.
param()

$adb  = "H:\Sdk\platform-tools\adb.exe"
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

& $adb shell pm clear com.mcpai.chat | Out-Null
& $adb shell am force-stop com.mcpai.chat | Out-Null
& $adb shell am start -n com.mcpai.chat/.ui.MainActivity `
    --es api_key $key `
    --es base_url "https://api.hcnsec.cn/v1" `
    --es model "MiMo-V2.6-Flash" | Out-Null
Start-Sleep -Seconds 9

& $adb shell input tap 600 96 | Out-Null
Start-Sleep -Seconds 3

Capture "36-settings-top"

# Scroll to the tool-confirmation and history controls.
& $adb shell input swipe 360 1100 360 500 400 | Out-Null
Start-Sleep -Seconds 2
Capture "37-settings-tools"

& $adb shell input swipe 360 1100 360 600 400 | Out-Null
Start-Sleep -Seconds 2
Capture "38-settings-history"
