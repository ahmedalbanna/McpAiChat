$aarDir = "A:\.gradle\caches\modules-2\files-2.1\androidx.security\security-crypto\1.1.0-alpha06"
$aarFile = Get-ChildItem -Path $aarDir -Filter "*.aar" | Select-Object -First 1 -ExpandProperty FullName
Write-Host "AAR: $aarFile"

$extractDir = "C:\Users\ahmed\McpAiChat/tools/security_crypto_extract"
if (-not (Test-Path $extractDir)) { New-Item -ItemType Directory -Path $extractDir | Out-Null }
Expand-Archive -Path $aarFile -DestinationPath $extractDir -Force

Write-Host "=== Classes in AAR ==="
Get-ChildItem -Path $extractDir -Filter "classes.jar" -Recurse | Select-Object FullName | Format-Table -AutoSize | Out-String

# List all class names from the jar
$jarFile = Get-ChildItem -Path $extractDir -Filter "classes.jar" -Recurse | Select-Object -First 1 -ExpandProperty FullName
if ($jarFile) {
    Write-Host "=== Class names ==="
    & 'C:\Program Files\Java\jdk-24\bin\jar.exe' tf $jarFile | Where-Object { $_ -match '\.class$' } | ForEach-Object { $_ -replace '/', '.' -replace '\.class$', '' } | Where-Object { $_ -match 'security|crypto|Encrypted|MasterKey|PrefKey' } | Format-Table -AutoSize | Out-String
}
