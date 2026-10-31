#!/usr/bin/env bash
# Installs the debug APK on the connected device and launches the app.
set -euo pipefail

ADB="${ANDROID_HOME:-H:/Sdk}/platform-tools/adb.exe"
APK="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK" ]; then
  echo "APK not found: $APK" >&2
  echo "Run: gradle :app:assembleDebug" >&2
  exit 1
fi

echo "==> devices"
"$ADB" devices

echo "==> installing"
"$ADB" install -r "$APK"

echo "==> launching"
"$ADB" shell am start -n com.mcpai.chat/.ui.MainActivity

echo
echo "Done. Useful commands:"
echo "  adb logcat -s McpAiChat:V"
echo "  adb shell input text 'hello'"
echo "  adb exec-out screencap -p > screen.png"
