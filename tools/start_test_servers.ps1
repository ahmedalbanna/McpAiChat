# Starts the reference MCP server and the mock OpenAI provider, then exposes
# both ports to the connected device with adb reverse.
#
#   tools\start_test_servers.ps1
param(
    [int]$McpPort = 8099,
    [int]$ProviderPort = 8098
)

$ErrorActionPreference = "Continue"

$root  = Split-Path $PSScriptRoot -Parent
$adb   = "H:\Sdk\platform-tools\adb.exe"
$logDir = Join-Path $root "logs"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

$python = "C:\ProgramData\anaconda3\python.exe"

function Start-Server($script, $port, $logName) {
    $existing = Get-CimInstance Win32_Process -Filter "Name='python.exe'" |
        Where-Object { $_.CommandLine -like "*$script*" -and $_.CommandLine -like "*--port $port*" }
    if ($existing) {
        Write-Host ("  {0} already running on {1}" -f $script, $port) -ForegroundColor Green
        return
    }

    $log = Join-Path $logDir $logName
    $args = "$script --port $port"
    Start-Process -FilePath $python -ArgumentList $args `
        -WorkingDirectory $root `
        -RedirectStandardOutput $log `
        -WindowStyle Hidden
    Write-Host ("  started {0} on {1}" -f $script, $port)
}

Write-Host "==> test servers"
Start-Server "tools/reference_mcp_server.py" $McpPort "refmcp.log"
Start-Server "tools/mock_openai_server.py" $ProviderPort "mockopenai.log"

Start-Sleep -Seconds 4

Write-Host "==> reachable from this PC?"
foreach ($port in @($McpPort, $ProviderPort)) {
    try {
        $reply = Invoke-WebRequest -Uri "http://127.0.0.1:$port/v1/models" -TimeoutSec 5 -UseBasicParsing -ErrorAction SilentlyContinue
        if ($reply.StatusCode -eq 200) {
            Write-Host ("  port {0}: 200 OK" -f $port) -ForegroundColor Green
        }
    } catch {
        # Not every server answers GET /v1/models; try an MCP initialize instead.
        try {
            $body = '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
            $reply = Invoke-WebRequest -Uri "http://127.0.0.1:$port/mcp" -Method POST `
                -ContentType "application/json" -Body $body -TimeoutSec 5 -UseBasicParsing
            Write-Host ("  port {0}: HTTP {1} (MCP answered)" -f $port, $reply.StatusCode) -ForegroundColor Green
        } catch {
            Write-Host ("  port {0}: not answering" -f $port) -ForegroundColor Yellow
        }
    }
}

Write-Host "==> adb reverse"
& $adb reverse tcp:$McpPort tcp:$McpPort | Out-Null
& $adb reverse tcp:$ProviderPort tcp:$ProviderPort | Out-Null
& $adb reverse --list

Write-Host @"

Both are now reachable from the device on 127.0.0.1.
Configure the app with:

  adb shell am start -n com.mcpai.chat/.ui.MainActivity \`
    --es api_key sk-test-test-test \`
    --es base_url http://127.0.0.1:$ProviderPort/v1 \`
    --es model mock-model \`
    --es mcp_url http://127.0.0.1:$McpPort/mcp
"@ -ForegroundColor Cyan
