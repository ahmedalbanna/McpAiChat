$adb = "H:/Sdk/platform-tools/adb.exe"
$docs = "C:\Users\ahmed\McpAiChat\docs"

function Capture($name) {
    $path = Join-Path $docs "$name.png"
    cmd /c "$adb exec-out screencap -p > `"$path`""
    $kb = [math]::Round((Get-Item $path).Length / 1KB, 1)
    Write-Host ("  -> {0}.png ({1} KB)" -f $name, $kb)
}

Write-Host "==> tap attach button at bottom left"
& $adb shell input tap 150 1224 > $null 2>&1
Start-Sleep -Seconds 4
Capture "58-attach-picker"
