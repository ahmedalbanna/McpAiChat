# Drives the app on the connected device: configure, navigate, type, send and
# capture screenshots.
#
#   tools\shot.ps1 -Profile hcnsec -Nav catalog -Shot 25-catalog         -> catalog screen
#   tools\shot.ps1 -Nav servers -Shot 26-servers
#
# The docblock at the bottom of this file lists every step keyword.
param(
    # Screenshot file name (without .png) written to docs\.
    [string]$Shot = "",

    # What to do before the screenshot: none | servers | settings | catalog | serveredit
    [ValidateSet("none", "servers", "settings", "catalog", "serveredit", "tools")]
    [string]$Nav = "none",

    # Text typed into the message field before sending.
    [string]$Type = "",

    # Actually tap Send after typing.
    [switch]$Send,

    # How long to wait after sending before the screenshot (the model streams).
    [int]$WaitFor = 25,

    # Wipe the app's data first, so the run starts from a clean state.
    [switch]$Clear
)

$ErrorActionPreference = "Continue"

$adb  = "H:\Sdk\platform-tools\adb.exe"
$root = Split-Path $PSScriptRoot -Parent
$docs = Join-Path $root "docs"
# The provider API key comes from the environment, never from the repository.
$key = $env:MCPAICHAT_API_KEY
if (-not $key) { throw "Set MCPAICHAT_API_KEY before running this script." }

# Toolbar / bottom-bar coordinates for a 720x1280 device.
$menuServers  = @{ X = 505; Y = 96 }
$menuSettings = @{ X = 600; Y = 96 }
$freeButton   = @{ X = 150; Y = 1210 }
$inputField   = @{ X = 280; Y = 1224 }
$sendButton   = @{ X = 640; Y = 715 }

function Tap($p) {
    & $adb shell input tap $p.X $p.Y | Out-Null
    Start-Sleep -Seconds 1
}

function Capture($name) {
    if (-not $name) { return }
    $path = Join-Path $docs "$name.png"
    # Redirecting in PowerShell would re-encode the bytes as UTF-16, so the
    # redirection is handed to cmd instead.
    & cmd /c "$adb exec-out screencap -p > `"$path`""
    if ((Test-Path $path) -and ((Get-Item $path).Length -gt 5000)) {
        $kb = [math]::Round((Get-Item $path).Length / 1KB, 1)
        Write-Host "  -> $name.png ($kb KB)" -ForegroundColor Green
    } else {
        Write-Host "  -> screenshot failed for $name" -ForegroundColor Yellow
    }
}

function Launch() {
    if ($Clear) {
        & $adb shell pm clear com.mcpai.chat | Out-Null
    }
    & $adb shell am force-stop com.mcpai.chat | Out-Null
    & $adb shell am start -n com.mcpai.chat/.ui.MainActivity `
        --es api_key $key `
        --es base_url "https://api.hcnsec.cn/v1" `
        --es model "MiMo-V2.6-Flash" | Out-Null
    Start-Sleep -Seconds 9
    Write-Host "  app launched"
}

Launch

switch ($Nav) {
    "servers"   { Tap $menuServers;  Start-Sleep -Seconds 4; Write-Host "  -> servers screen" }
    "settings"  { Tap $menuSettings; Start-Sleep -Seconds 4; Write-Host "  -> settings screen" }
    "catalog"   {
        Tap $menuServers
        Start-Sleep -Seconds 4
        Tap $freeButton
        Start-Sleep -Seconds 4
        Write-Host "  -> free-server catalogue"
    }
    "tools"     { Tap $menuServers; Start-Sleep -Seconds 4; Write-Host "  -> tools screen" }
}

if ($Type) {
    Tap $inputField
    Start-Sleep -Seconds 2
    $encoded = $Type.Replace(" ", "%s")
    & $adb shell input text $encoded | Out-Null
    Start-Sleep -Seconds 2
    Write-Host "  typed: $Type"
}

if ($Send) {
    Tap $sendButton
    Write-Host "  sent, streaming for ${WaitFor}s"
    Start-Sleep -Seconds $WaitFor
    # Dismiss the keyboard so the answer is not covered.
    & $adb shell input keyevent 111 | Out-Null
    Start-Sleep -Seconds 2
}

Capture $Shot
