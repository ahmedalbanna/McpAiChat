# MCP AI Chat

An Android client for the **Model Context Protocol** with an OpenAI-compatible
model provider. The app talks to one or more MCP tool servers, exposes their
tools to the model, executes the tool calls the model requests, and streams the
answer back into the conversation.

The UI, message-state model and adapter layout were modelled on the interface
patterns recovered from four commercial Android apps (the analysis lives
outside this repository, in `../mcp-ai-chat-re/`).

---

## Features

| Area                      | What it does                                                                                                                                                                                                                                                     |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **MCP client**            | JSON-RPC 2.0, `initialize` handshake, `tools/list`, `tools/call`, `resources/list`, `resources/read`, `prompts/list`, `prompts/get`, `ping`                                                                                                                      |
| **Transports**            | Streamable HTTP (with SSE replies and `Mcp-Session-Id` tracking) is what the app uses. A stdio transport (local process) exists in the client library and is covered by tests, but the phone UI configures servers by URL only                                   |
| **AI provider**           | OpenAI-compatible `/chat/completions` with SSE streaming and native tool/function calling — works with OpenAI, OpenRouter, Groq, Together, LiteLLM, vLLM, Ollama's `/v1` shim and `api.hcnsec.cn`                                                                |
| **Reasoning models**      | Streams `delta.reasoning` / `delta.reasoning_content` and shows the trace above the answer                                                                                                                                                                       |
| **Model picker**          | Loads the provider's live model list from `GET /v1/models`                                                                                                                                                                                                       |
| **Tool loop**             | `user → LLM → tool_calls → MCP tools/call → tool result → LLM → answer`, bounded to 8 rounds                                                                                                                                                                     |
| **Tool approval**         | Every tool call asks first — tool name, owning server and arguments — with optional per-tool auto-approval that can be revoked                                                                                                                                   |
| **Free server catalogue** | 33 keyless public MCP servers, each verified with a live handshake; suggestions only, all editable                                                                                                                                                               |
| **Chat UI**               | Streaming assistant bubbles, dedicated tool-call cards (arguments + result + status), status banner, per-server enable switch, stop generating mid-turn, jump-to-latest while scrolled up, suggestion chips on the empty state, per-message copy of raw markdown |
| **Storage**               | SQLite conversations and messages; the assistant answer is persisted while it streams                                                                                                                                                                            |
| **Servers screen**        | Add / edit / enable / delete MCP servers, live connection probe, tool catalogue, JSON export and import                                                                                                                                                          |
| **Settings screen**       | API key, base URL, model, system prompt, temperature, history limit, "allow tools" switch                                                                                                                                                                        |
| **Security**              | HTTPS only (cleartext is refused except on loopback), optional SPKI certificate pinning per trusted server, AES-256-GCM encrypted preferences backed by the Android Keystore                                                                                     |

No third-party runtime dependencies beyond AndroidX, Material and
`kotlinx-coroutines`: JSON uses the platform `org.json`, HTTP uses
`HttpURLConnection`, persistence uses `SQLiteOpenHelper`.

---

## Project layout

```
app/src/main/java/com/mcpai/chat/
├── mcp/                 ← the protocol layer
│   ├── McpClient.kt        JSON-RPC 2.0 + MCP operations
│   ├── McpTransport.kt     transport interface
│   ├── HttpMcpTransport.kt Streamable HTTP (+ SSE) + SPKI capture/verification
│   ├── StdioMcpTransport.kt local process transport
│   ├── McpRegistry.kt      multi-server registry + tool routing
│   ├── McpModels.kt        Tool / Resource / Prompt / result types
│   ├── SpkiPinVerifier.kt  pure pin comparison (first time / match / mismatch)
│   ├── SpkiPinPolicy.kt    which pin survives an edit
│   ├── ServerUrlValidator.kt HTTPS-only URL rules
│   ├── ConnectionRetryPolicy.kt lazy connect + one retry
│   └── FreeMcpCatalog.kt   33 verified keyless public servers
├── llm/                 ← the model layer
│   ├── OpenAiProvider.kt   streaming chat-completions
│   └── LlmModels.kt        ChatMessage / ToolCall / LlmEvent
├── engine/
│   └── ChatEngine.kt       the LLM ⇄ MCP tool loop + approval gate
├── data/
│   ├── Settings.kt             server + provider configuration
│   ├── EncryptedPreferences.kt  AES-256-GCM over the Android Keystore
│   ├── ServerConfigSerializer.kt server config (de)serialisation
│   ├── SettingsExporter.kt      JSON export / import
│   ├── ToolApprovalStore.kt     per-(server, tool) auto-approval
│   ├── ChatDatabase.kt          SQLite conversations + messages
│   ├── ChatRepository.kt        facade, emits ChatEvent stream
│   └── TurnHistory.kt           the conversation turn model
├── util/
│   ├── ChatError.kt   error categories (SPKI mismatch / unavailable / unknown)
│   ├── Io.kt          file helpers
│   └── Json.kt        org.json helpers
└── ui/
    ├── MainActivity.kt              chat screen (Compose)
    ├── ChatScreen.kt / ChatRows.kt Compose rows, bubbles and tool-group list
    ├── ToolApprovalDialog.kt        Material 3 approval dialog
    ├── ConversationListActivity.kt   conversation list (launcher)
    ├── ServersActivity.kt            MCP server management
    ├── CatalogActivity.kt            free-server catalogue
    ├── ServerEditActivity.kt         add / edit one server
    ├── SettingsActivity.kt           provider, history and tool settings
    └── ToolDetailActivity.kt         tool catalogue + auto-approve toggles
```

---

## Build

Requirements: Android SDK (`compileSdk 36`), JDK 21 toolchain (Java 17 bytecode).

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="H:/Sdk"

cd McpAiChat
gradle :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

This repository has no `gradlew` wrapper — use the system Gradle.

Install on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Unit tests:

```bash
gradle :app:testDebugUnitTest
# -> app/build/test-results/testDebugUnitTest/
```

Device tests — these cover what the JVM cannot: the Android Keystore, SQLite,
the Android HTTP stack and the network security config (a config that fails to
parse on API 26 silently kills every HTTPS call in the app):

```bash
gradle :app:connectedDebugAndroidTest
# -> app/build/reports/androidTests/connected/debug/
```

---

## Use

1. **Settings → AI provider**
   - API key: your OpenAI key
   - Base URL: `https://api.openai.com/v1` (or your gateway)
   - Model: e.g. `gpt-4o-mini`
   - Optionally a system prompt, temperature and a history limit

2. **MCP servers → Add server**
   - Name: anything
   - URL: `https://<host>/mcp` — a missing scheme defaults to `https://`
   - Auth header name / value: e.g. `Authorization` / `Bearer <key>`; leave empty
     for keyless servers
   - Request timeout in seconds (default 30)
   - **Trusted domain**: pins the server's certificate, see [Security](#security)
   - Plain HTTP is accepted only for loopback addresses (`127.0.0.1`,
     `localhost`, `10.0.2.2`), where a local development server lives
   - Tap **Test** to run a real `initialize` + `tools/list` round trip

3. **Chat** — the model receives every tool advertised by the enabled servers and
   can call them. Tool calls appear as cards with the arguments and the result.

### Fast configuration over adb (debug builds only)

```bash
adb reverse tcp:8099 tcp:8099      # expose a local MCP server to the device
adb reverse tcp:8098 tcp:8098      # expose a local provider

adb shell am start -n com.mcpai.chat/.ui.MainActivity \
  --es api_key "sk-..." \
  --es base_url "http://127.0.0.1:8098/v1" \
  --es model "gpt-4o-mini" \
  --es mcp_url "http://127.0.0.1:8099/mcp"
```

---

## Security

**HTTPS only.** Remote servers must be HTTPS. Cleartext HTTP is disabled app-wide
(`usesCleartextTraffic="false"` plus a network security config whose base config
refuses cleartext); only loopback hosts are exempted, for local development.

**Certificate pinning, per server.** Marking a server as a _trusted domain_
records the SHA-256 hash of its SubjectPublicKeyInfo (SPKI, `sha256:` + base64)
and pins it from then on. If the server presents a different certificate the
connection is rejected and a **Re-trust** action is offered — the pin is never
updated silently. Re-trusting means accepting a new certificate by hand, which
is the only safe response when a certificate may have rotated.

**Encrypted storage.** The provider API key, the server list and the auth header
values are stored in `SharedPreferences` encrypted with AES-256-GCM. The master
key lives in the Android Keystore and never leaves it; each value gets a fresh
random IV. The preferences file is excluded from cloud backup and from
device-to-device transfer (`res/xml/backup_rules.xml`,
`res/xml/data_extraction_rules.xml`).

**Approval before every call.** An MCP server is third-party code. The app asks
before running a tool and shows the tool name, the server that owns it and the
pretty-printed arguments.

---

## Verified on a real device

Built and run on a Samsung SM-G930P (Android 8.0 / API 26) against a live MCP
server and the live `https://api.hcnsec.cn/v1` provider. The full loop works:

```
user message
   → OpenAI-compatible /chat/completions (SSE streaming)
   → tool_calls in the stream
   → MCP tools/call over Streamable HTTP
   → tool result
   → back to the model
   → final answer
```

Confirmed with `MiMo-V2.6-Flash` and `Qwen3.8-Flash-Next`:

```
user: "use add tool for 15 plus 27"
  reasoning: "Simple."
  tool call: add {"a": 15, "b": 27}  → 42.0
  answer:    "15 + 27 = 42.0"
```

Screenshots and the captured provider payloads are in `docs/`, and
`docs/VERIFICATION.md` lists every check plus the nine bugs that were found and
fixed during the run.

---

## Free MCP servers

The **Free MCP servers** button on the servers screen opens a catalogue of 33
public MCP servers that need no key. Every entry was verified with a live
`initialize` + `tools/list` handshake on **2026-10-29** — see
[FREE-MCP-SERVERS.md](FREE-MCP-SERVERS.md) for the method, the results for the
commercial vendors (Notion, Linear, Figma, Sentry, Zapier, GitHub… all require
a token), and the security warning.

The catalogue is a set of _suggestions_: pick one and it is added as an ordinary
server that you can rename, edit, disable or delete like any other.

Re-check the catalogue any time:

```bash
python tools/verify_catalog.py
```

---

## Tool approval

Every tool call the model asks for is confirmed first. The dialog shows the
tool name, the server that owns it, and the pretty-printed arguments. Denying
tells the model the call did not run, so it can answer from what it already
knows.

Two checkboxes sit under the message:

- **Trust this server for this conversation** — skips the dialog for the rest
  of the conversation only.
- **Auto-approve this tool** — stores a durable, per-(server, tool) rule in
  SQLite. Revoke it at any time from the tool catalogue screen, where every tool
  has an auto-approve switch.

Approving every call is the default. It can be switched off entirely in
**Settings** — but an MCP server is third-party code, so leave it on unless you
control the endpoint.

---

## Reference servers (for testing)

A dependency-free MCP server implementing the subset of MCP the app uses:

```bash
python tools/reference_mcp_server.py --port 8099
```

Tools: `echo`, `now`, `add`, `read_file`. It also serves a resource and a prompt.

A mock OpenAI-compatible provider that forces a tool call and then answers with
the tool result:

```bash
python tools/mock_openai_server.py --port 8098
```

Both, plus the `adb reverse` tunnels, are started by:

```powershell
powershell tools\start_test_servers.ps1
```

The same server is also available over **stdio** — newline-delimited JSON-RPC on
stdin/stdout, which is what a local MCP binary speaks:

```bash
python tools/stdio_mcp_server.py
```

That script is the reference binary the `stdio` transport is tested against. It
is also what you need on a phone to use the `stdio` transport at all: the app
configures servers by URL, so a `stdio` server can only be reached through the
client library or a device that can run a process of its own.

---

## Notes and limits

- The app sends the conversation history to the provider on every turn. The
  amount sent is capped by **Settings → history limit** (`settings.historyLimit`,
  default 40 turns; the slider offers 4–120 in steps of 4).
- The `stdio` transport needs a local MCP binary that can run on the device,
  which is only practical on a rooted device or inside Termux. The phone UI
  configures servers by URL, so it does not reach the `stdio` transport at all.
- Export writes the server list as JSON with the auth header values and the
  SPKI pins omitted — the export screen never leaks them. Importing a JSON file
  restores the servers it names; check the file before sharing it.
- The catalogue of public servers is a snapshot from 2026-10-29; the endpoints
  are third-party and can change or disappear at any time.
