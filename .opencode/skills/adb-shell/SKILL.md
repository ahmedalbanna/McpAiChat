---
name: ADB Shell
description: Drive the McpAiChat app on the attached Android device over adb — build and install the debug APK, launch and navigate the UI by taps, read screen text and dialogs with uiautomator, capture screenshots, read logcat, and probe the device's network and TLS stack. Use when installing the app on the phone, reproducing a crash or an error dialog, confirming a fix on the device, checking reachability from the phone, or any other device-side debugging.
---

# ADB Shell (McpAiChat on device)

The phone is the **proof surface**: this app's real failures live on the device (Keystore, TLS, layout), not in unit tests. One loop, repeated until the screen shows the expected state:

**build → install → launch → observe → act**

## The loop

1. **Build** — `gradle app:assembleDebug` (this repo has no `gradlew` wrapper; system Gradle 9.3.1). APK: `app/build/outputs/apk/debug/app-debug.apk`.
2. **Install** — `adb install -r app/build/outputs/apk/debug/app-debug.apk` → expect `Success`.
3. **Launch** — `adb shell am start -n com.mcpai.chat/.ui.ConversationListActivity`.
   Only `ConversationListActivity` is exported; `MainActivity`, `ServersActivity`, `SettingsActivity` are `exported="false"`, so `am start` on them ends in a permission denial. Reach them by tapping.
4. **Observe** — dump the screen, read the exact text, then act on what it says. Never guess an error from a dialog title.
5. **Act** — tap, type, or fix the code and repeat from step 1.

Done means the screen shows the expected text — not that the command exited 0.

## Observe: read the screen without OCR

`uiautomator` gives exact on-screen text and tap targets:

```powershell
adb shell uiautomator dump /sdcard/ui.xml
adb shell cat /sdcard/ui.xml
```

Extract every visible string:

```powershell
adb shell cat /sdcard/ui.xml | Select-String -Pattern 'text="[^"]+"' -AllMatches |
  ForEach-Object { $_.Matches } | ForEach-Object { $_.Value }
```

Extract one node's tap bounds (text first, then `bounds="[x1,y1][x2,y2]"`):

```powershell
adb shell cat /sdcard/ui.xml |
  Select-String -Pattern 'text="Allow"[^>]*bounds="\[[0-9,]+\]\[[0-9,]+\]"' -AllMatches |
  ForEach-Object { $_.Matches } | ForEach-Object { $_.Value }
```

Tap the centre of those bounds. **Read bounds, never reuse coordinates**: the keyboard slides the input bar up, so a Send button tapped at its pre-keyboard position silently does nothing — the message just sits in the field. Re-dump after every layout change.

PowerShell transcodes non-ASCII on the way out (`…` becomes `ΓÇª`), so piped text can lie about a character. Decode it byte-exactly:

```powershell
$b64 = (adb shell base64 /sdcard/ui.xml | Out-String) -replace '\s',''
$xml = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b64))
```

## Navigate (this device, 720x1280)

| Target               | How                                                                                   |
| -------------------- | ------------------------------------------------------------------------------------- |
| Chat screen          | tap a conversation row in the list (~200,201)                                         |
| MCP servers          | toolbar button (~506,96)                                                              |
| AI provider settings | toolbar button (~603,96)                                                              |
| Tool approval dialog | `Allow` / `Deny` buttons, plus the auto-approve checkbox                              |
| Send a message       | tap the field, `adb shell input text "Say%sOK"` (`%s` = space), then tap `sendButton` |

`adb shell input keyevent 4` goes back. `adb shell am force-stop com.mcpai.chat` resets to a cold start.

## Screenshot

```powershell
adb shell screencap -p /sdcard/shot.png
adb pull /sdcard/shot.png <local-path>
```

Then read the PNG with the `read` tool — it renders images, so you can look at it. PowerShell redirection corrupts binaries (`adb exec-out screencap -p > file` yields an unreadable file); always go through the device.

## Logs

```powershell
adb logcat -c                                   # clear before reproducing
$pid_app = (adb shell pidof com.mcpai.chat).Trim()
adb logcat -d --pid=$pid_app                    # app-only noise-free stream
adb logcat -d -s McpTransport                   # by tag
adb logcat -d -b crash                          # crash buffer
```

The app logs almost nothing on its own, so a swallowed exception leaves no trace. To see one, add a temporary `Log.e(TAG, message, throwable)` in the catch block, log the **root cause** (walk `cause` to the end, not the wrapper), rebuild, install, reproduce, read it — then keep or remove the logging deliberately.

## App data on the device

```powershell
adb shell run-as com.mcpai.chat ls -la shared_prefs
adb shell run-as com.mcpai.chat cat shared_prefs/mcp_ai_chat_encrypted.xml
```

Settings are AES-GCM encrypted, so the file shows ciphertext only — use it to confirm _which_ preferences exist, not their values. The device has **no `sqlite3` binary**; read app state from a UI dump instead.

## Network and TLS from the phone

```powershell
adb shell "curl -s -o /dev/null -w 'http=%{http_code}\n' --max-time 20 https://host/path"
adb shell ping -c 1 -W 3 host
adb shell "which curl nc wget toybox"
```

`curl` on the device uses BoringSSL; the app uses the platform Conscrypt stack. **curl succeeding does not mean the app's TLS works, and curl failing does not mean the app is broken** — test the two separately. Shell quoting mangles JSON, so push the payload first:

```powershell
adb push init.json /data/local/tmp/init.json
adb shell "curl -s -i --max-time 25 -X POST https://host/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' -d @/data/local/tmp/init.json"
```

## Local MCP server

```powershell
adb reverse tcp:8099 tcp:8099
```

The phone then reaches the PC's server at `http://127.0.0.1:8099`. Cleartext HTTP is permitted **only** for `127.0.0.1` / `localhost` (see `app/src/main/res/xml/network_security_config.xml`); every other host must be HTTPS.

## Known failure signatures

| Symptom                                                                                                       | Cause                                                                                                                                                                                                     |
| ------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `IllegalStateException: No factory found.` / `Failed to parse XML configuration from network_security_config` | The platform cannot parse `network_security_config.xml`, so **all** HTTPS in the app dies (MCP servers _and_ the LLM provider) while `curl` still works. The parser rules live in the repo's `AGENTS.md`. |
| `Unable to destroy activity … lateinit property repo has not been initialized`                                | `MainActivity` finishes early (no servers configured) and `onDestroy` touches an uninitialised `lateinit`.                                                                                                |
| `InvalidAlgorithmParameterException: Caller-provided IV not permitted`                                        | Android Keystore rejects a caller-supplied IV; let the Keystore generate it (`cipher.init(Cipher.ENCRYPT_MODE, key)` then read `cipher.iv`).                                                              |

## Device

Samsung SM-G930P, Android 8.0 (API 26), 720x1280, serial `ae7e8401`. `adb devices` confirms it is attached. Toolchain: JDK 21 (`jvmToolchain(21)`), bytecode level Java 17.
