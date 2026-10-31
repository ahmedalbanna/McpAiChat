# Scrolls the catalogue to the bottom and captures the tail entries.
param()

$adb  = "H:\Sdk\platform-tools\adb.exe"
$docs = "C:\Users\ahmed\McpAiChat\docs"

function Capture($name) {
    $path = Join-Path $docs "$name.png"
    & cmd /c "$adb exec-out screencap -p > `"$path`""
    $kb = [math]::Round((Get-Item $path).Length / 1KB, 1)
    Write-Host ("  -> {0}.png ({1} KB)" -f $name, $kb)
}

& $adb shell am force-stop com.mcpai.chat | Out-Null
& $adb shell am start -n com.mcpai.chat/.ui.MainActivity | Out-Null
Start-Sleep -Seconds 8

& $adb shell input tap 505 96 | Out-Null
Start-Sleep -Seconds 3
& $adb shell input tap 150 1210 | Out-Null
Start-Sleep -Seconds 4

Capture "30-catalog-top"

# Swipe up three times to reach the last cards.
for ($i = 1; $i -le 4; $i++) {
    & $adb shell input swipe 360 1100 360 300 400 | Out-Null
    Start-Sleep -Seconds 2
}

Capture "31-catalog-bottom"
