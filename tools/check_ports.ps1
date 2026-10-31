# Reports which local test ports are listening, so a device test knows whether
# the reference MCP server and the mock provider are still up.
param([int[]]$Ports = @(8099, 8098, 8080))

$listening = @{}
$connections = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue
foreach ($c in $connections) {
    $listening[$c.LocalPort] = $true
}

foreach ($port in $Ports) {
    $state = if ($listening.ContainsKey($port)) { "UP" } else { "down" }
    $color = if ($listening.ContainsKey($port)) { "Green" } else { "DarkGray" }
    Write-Host ("port {0}: {1}" -f $port, $state) -ForegroundColor $color
}
