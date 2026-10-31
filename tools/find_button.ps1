# Dumps the accessibility tree and lists the clickable nodes with their centres,
# so a test can tap the right control without guessing coordinates.
#   tools\find_button.ps1
param([string]$Text = "")

$adb = "H:\Sdk\platform-tools\adb.exe"
$local = "C:\Users\ahmed\McpAiChat\logs\ui.xml"

& $adb shell uiautomator dump /sdcard/window_dump.xml 2>$null | Out-Null
& $adb shell cat /sdcard/window_dump.xml > $local

if (-not (Test-Path $local)) {
    Write-Host "no dump produced" -ForegroundColor Red
    exit 1
}

$xml = Get-Content $local -Raw

# Split into node elements; attribute order differs between API levels.
$nodes = [regex]::Matches($xml, "<node\b[^>]*/?>") 

$rows = foreach ($node in $nodes) {
    $tag = $node.Value

    $attr = [regex]::Match($tag, 'text="([^"]*)"')
    $desc = [regex]::Match($tag, 'content-desc="([^"]*)"')
    $bnd = [regex]::Match($tag, 'bounds="(\[\d+,\d+\]\[\d+,\d+\])"')
    $clk = [regex]::Match($tag, 'clickable="(true|false)"')

    $label = $attr.Groups[1].Value
    if ([string]::IsNullOrWhiteSpace($label)) { $label = $desc.Groups[1].Value }

    [pscustomobject] @{
        Label  = $label
        Bounds = $bnd.Groups[1].Value
        Click  = $clk.Groups[1].Value
    }
}

if ($Text) {
    $rows = $rows | Where-Object { $_.Label -and $_.Label.ToLower().Contains($Text.ToLower()) }
}

foreach ($r in $rows) {
    $nums = [regex]::Matches($r.Bounds, "\d+")
    if ($nums.Count -ge 4) {
        $x = ([int]$nums[0].Value + [int]$nums[2].Value) / 2
        $y = ([int]$nums[1].Value + [int]$nums[3].Value) / 2
        $centre = "center=({0},{1})" -f [int]$x, [int]$y
    } else { $centre = "" }

    $marker = if ($r.Click -eq "true") { "*" } else { " " }
    Write-Host ("{0} '{1}'  {2}  {3}" -f $marker, $r.Label, $r.Bounds, $centre)
}
