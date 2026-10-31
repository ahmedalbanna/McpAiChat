# Verifies the catalogue -> server-list flow:
#   1. open the free-server catalogue
#   2. tap "Add server" on the first entry
#   3. confirm we are back on the servers screen with the entry saved
param()

$ErrorActionPreference = "Continue"

$adb  = "H:\Sdk\platform-tools\adb.exe"
$root = Split-Path $PSScriptRoot -Parent
$docs = Join-Path $root "docs"
# The provider API key comes from the environment, never from the repository.
$key = $env:MCPAICHAT_API_KEY
if (-not $key) { throw "Set MCPAICHAT_API_KEY before running this script." }

function Tap($x, $y) {
    & $adb shell input tap $x $y | Out-Null
    Start-Sleep -Seconds 1
}

function Capture($name) {
    $path = Join-Path $docs "$name.png"
    & cmd /c "$adb exec-out screencap -p > `"$path`""
    $kb = [math]::Round((Get-Item $path).Length / 1KB, 1)
    Write-Host ("  -> {0}.png ({1} KB)" -f $name, $kb)
}

Write-Host "==> clean start"
& $adb shell pm clear com.mcpai.chat | Out-Null
& $adb shell am force-stop com.mcpai.chat | Out-Null
& $adb shell am start -n com.mcpai.chat/.ui.MainActivity `
    --es api_key $key `
    --es base_url "https://api.hcnsec.cn/v1" `
    --es model "MiMo-V2.6-Flash" | Out-Null
Start-Sleep -Seconds 9

Write-Host "==> servers screen"
Tap 505 96
Start-Sleep -Seconds 4

Write-Host "==> free-server catalogue"
Tap 150 1210
Start-Sleep -Seconds 4
Capture "27-catalog-before"

# "Add server" sits at the bottom of the first catalogue card (DeepWiki).
Write-Host "==> tapping Add server on DeepWiki"
Tap 200 372
Start-Sleep -Seconds 4
Capture "28-after-add"

Write-Host "==> which screen are we on?"
$focus = & $adb shell dumpsys window 2>$null | Select-String "mCurrentFocus"
Write-Host "  $focus"

Write-Host "==> stored servers"
$prefs = & $adb shell run-as com.mcpai.chat cat files/../shared_prefs/mcp_ai_chat.xml 2>$null
if ($prefs) {
    $line = ($prefs | Select-String "mcp_servers")
    Write-Host "  $line"
} else {
    Write-Host "  (not readable without a debuggable build)"
}
