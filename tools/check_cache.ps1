# Reports whether an artifact is already in the local Gradle module cache, so a
# build can plan around the slow network (~75 KB/s) instead of guessing.
param([string[]]$Artifacts = @("androidx.security", "androidx.security.security-crypto"))

$cache = "A:\.gradle\caches\modules-2\files-2.1"

if (-not (Test-Path $cache)) {
    Write-Host "no module cache at $cache" -ForegroundColor Red
    exit 1
}

$dirs = Get-ChildItem $cache -Directory

foreach ($artifact in $Artifacts) {
    $hit = $dirs | Where-Object { $_.Name -like "*$artifact*" }
    if ($hit) {
        foreach ($h in $hit) {
            $versions = Get-ChildItem $h.FullName -Directory | Select-Object -ExpandProperty Name
            Write-Host ("FOUND {0}  versions: {1}" -f $h.Name, ($versions -join ", ")) -ForegroundColor Green
        }
    } else {
        Write-Host ("absent {0}" -f $artifact) -ForegroundColor Yellow
    }
}

Write-Host "`ntotal cached groups: $($dirs.Count)"
Write-Host "cache size: $([math]::Round((Get-ChildItem $cache -Recurse -File | Measure-Object -Property Length -Sum).Sum / 1GB, 2)) GB"
