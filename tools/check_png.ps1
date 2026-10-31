# Reports the size and PNG magic bytes of a file, plus the newest docs\*.png.
param([Parameter(Mandatory = $true)][string]$Path)

if (-not (Test-Path $Path)) {
    Write-Host "missing: $Path" -ForegroundColor Red
    exit 1
}

$info = Get-Item $Path
Write-Host ("size={0} bytes" -f $info.Length)

$bytes = [System.IO.File]::ReadAllBytes($Path)
$head = $bytes[0..7] -join " "
Write-Host ("magic=$head")

if ($bytes.Length -gt 8 -and $bytes[1] -eq 0x50 -and $bytes[2] -eq 0x4E -and $bytes[3] -eq 0x47) {
    Write-Host "valid PNG" -ForegroundColor Green

    # PNG stores width/height in the IHDR chunk, big-endian.
    $w = ($bytes[16] * 16777216) + ($bytes[17] * 65536) + ($bytes[18] * 256) + $bytes[19]
    $h = ($bytes[20] * 16777216) + ($bytes[21] * 65536) + ($bytes[22] * 256) + $bytes[23]
    Write-Host ("dimensions={0}x{1}" -f $w, $h)
} else {
    Write-Host "NOT a PNG" -ForegroundColor Red
    $head = $bytes[0..79] | ForEach-Object { $_ } | Out-String
    Write-Host "first bytes as text:"
    Write-Host ($bytes[0..([math]::Min(79, $bytes.Length - 1))] -join " ")
}

Write-Host "`nnewest docs\*.png:"
Get-ChildItem (Split-Path $Path | Split-Path -Parent) -Filter *.png |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 5 Name, Length |
    Format-Table -AutoSize | Out-String | Write-Host
