$aarFile = "A:\.gradle\caches\modules-2\files-2.1\androidx.security\security-crypto\1.1.0-alpha06\d23f6c36fbbeea99668bf077e14184bb2d4fc7d1\security-crypto-1.1.0-alpha06.aar"
$extractDir = "C:\Users\ahmed\McpAiChat/tools/security_crypto_extract"
if (-not (Test-Path $extractDir)) { New-Item -ItemType Directory -Path $extractDir | Out-Null }
Expand-Archive -Path $aarFile -DestinationPath $extractDir -Force

Write-Host "=== Contents of AAR ==="
Get-ChildItem -Path $extractDir -Recurse | Select-Object Name, Length | Format-Table -AutoSize | Out-String

$jarFile = Join-Path $extractDir "classes.jar"
if (Test-Path $jarFile) {
    Write-Host "=== Class names from classes.jar ==="
    & 'C:\Program Files\Java\jdk-24\bin\jar.exe' tf $jarFile | Where-Object { $_ -match '\.class$' } | ForEach-Object { $_ -replace '/', '.' -replace '\.class$', '' } | Where-Object { $_ -match 'security|crypto|Encrypted|MasterKey|PrefKey|PrefValue' } | Format-Table -AutoSize | Out-String
} else {
    Write-Host "No classes.jar found!"
}
