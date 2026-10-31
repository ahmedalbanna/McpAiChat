param([Parameter(Mandatory = $true)][string]$Out)
& cmd /c "`"H:\Sdk\platform-tools\adb.exe`" exec-out screencap -p > `"$Out`""
