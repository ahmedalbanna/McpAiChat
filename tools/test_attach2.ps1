$adb = "H:/Sdk/platform-tools/adb.exe"
$docs = "C:\Users\ahmed\McpAiChat\docs"

function Capture($name) {
    $path = Join-Path $docs "$name.png"
    cmd /c "$adb exec-out screencap -p > `"$path`""
    $kb = [math]::Round((Get-Item $path).Length / 1KB, 1)
    Write-Host ("  -> {0}.png ({1} KB)" -f $name, $kb)
}

Write-Host "==> launch app"
& $adb shell am force-stop com.mcpai.chat | Out-Null
& $adb shell am start -n com.mcpai.chat/.ui.MainActivity > $null 2>&1
Start-Sleep -Seconds 8
Capture "56-app-launched"

Write-Host "==> tap attach area (guessing X=500 Y=1224)"
& $adb shell input tap 500 1224 > $null 2>&1
Start-Sleep -Seconds 4
Capture "57-after-attach-tap"
