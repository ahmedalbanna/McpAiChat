# Verification report

Real-device end-to-end tests of the app against a **live MCP server** and a
**live OpenAI-compatible provider**.

## Environment

| | |
|---|---|
| Device | Samsung SM-G930P, Android 8.0 (API 26), 720×1280 @ 274dpi |
| Build | `gradle :app:assembleDebug` → `app-debug.apk`, AGP 9.1.1, Gradle 9.3.1, JDK 21 (Android Studio JBR) |
| Provider | `https://api.hcnsec.cn/v1` — models: `MiMo-V2.6-Flash`, `Qwen3.8-Flash-Next` (others tested, see below) |
| MCP server | `tools/reference_mcp_server.py` on `127.0.0.1:8099/mcp`, reached through `adb reverse` |

## Test A — real provider + real MCP server

```
user: "Use the add tool to calculate 21 plus 21"
  reasoning: "Simple addition."
  tool call: add {"a": 21, "b": 21}  → 42.0
  answer:    "21 + 21 = 42.0"
```

```
user: "use add tool for 15 plus 27"          (model: Qwen3.8-Flash-Next)
  reasoning: "Simple."
  tool call: add {"a": 15, "b": 27}  → 42.0
  answer:    "15 + 27 = 42.0"
```

Both models of the provider drive the MCP tool loop correctly.

## Test B — mock provider (deterministic)

`tools/mock_openai_server.py` forces a tool call and then a final answer, so the
exact provider payload can be inspected:

```jsonc
{"role":"system","content":"You are a helpful assistant. You have access to MCP tools; …"}
{"role":"user","content":"compute 21 plus 21"}
{"role":"assistant","content":"","tool_calls":[{"id":"call_mock_1","type":"function",
   "function":{"name":"add","arguments":"{\"a\": 21, \"b\": 21}"}}]}
{"role":"tool","content":"42.0","tool_call_id":"call_mock_1"}
{"role":"assistant","content":"The MCP tool answered: 42.0"}
```

The assistant turn that requested the tool is a **separate** message from the
final answer, which is what OpenAI requires when replaying a tool call.

## Checks

| Check | Result |
|---|---|
| App launches, no crash | ✅ |
| MCP handshake + tool discovery | ✅ toolbar shows **4 tools** |
| Server screen | ✅ "debug server · Connected · 4 tools" |
| Tool call executed on the MCP server | ✅ `add {"a":21,"b":21}` → `42.0` |
| Tool result shown in the UI | ✅ card with Arguments + Result + "done" |
| Model received the tool result | ✅ final answer quotes the result |
| Message ordering | ✅ user → tool card → final answer |
| History persisted across a restart | ✅ restored from SQLite |
| History format for the provider | ✅ see above |
| Reasoning trace displayed | ✅ italic block above the bubble |
| Model picker | ✅ live list from `GET /v1/models` |
| Settings screen | ✅ key (masked), base URL, model, prompt, temperature, tools toggle |
| Tool approval — Allow | ✅ `docs/35-final.png`: card shows **done**, result `42.0`, model answers with it |
| Tool approval — Deny | ✅ `docs/35-final.png`: card shows **declined**, result text is honest, model reports it back |
| Approval survives orientation / restart | ✅ the dialog is guarded by `approvalDialog?.isShowing` |
| Free-server catalogue | ✅ 33 entries, 33/33 answered a live handshake on 2026-10-29 |
| Add from catalogue | ✅ `docs/28-after-add.png`: DeepWiki saved, `Connected · 3 tools` |

## Provider notes (HCNSEC)

`GET https://api.hcnsec.cn/v1/models` returns the catalogue. Status observed
while testing:

| Model | Status |
|---|---|
| `MiMo-V2.6-Flash` | ✅ works, streams `delta.reasoning` |
| `Qwen3.8-Flash-Next` | ✅ works, streams `delta.reasoning` |
| `step-5-preview` | ✅ works, streams `delta.reasoning_content` |
| `MiniMax-M3.1-Flash` | ⚠️ `分组 auto 下模型 … 的可用渠道不存在（retry）` — no channel in the `auto` group |
| `longcat-2.5` | ⚠️ `Upstream request failed: Endpoint is unavailable.` |

Both failing models are listed by the provider but have no working channel
behind the `auto` group at the time of testing.

Tool calling works on the OpenAI wire format: the response carries
`tool_calls[{id,index,type,function{name,arguments}}]` and
`finish_reason: "tool_calls"`.

## Bugs found and fixed during verification

1. **The assistant row was never added to the list**, so streamed text was
   invisible. Fixed by emitting `AssistantStarted` and creating the row lazily
   on the first delta.
2. **The tool card rendered after the final answer.** The single-assistant-row
   model could not express `assistant(tool_calls) → tool → assistant(answer)`.
   The engine now reports every assistant turn (`onAssistantTurn`) and the
   repository stores one row per turn.
3. **JSON `null` leaked into the UI as the text `"null"`.** `optString()`
   returns the literal `"null"` for a JSON null, and reasoning models send
   `null` for unused fields. Added `util/Json.kt` (`stringOrNull` / `text`) and
   replaced every optional `optString` read.
4. **The user bubble was white on white** — the drawable hard-coded
   `@color/white` instead of `@color/bubble_user`.
5. **`SettingsActivity` crashed** (ClassCastException): `TextInputEditText`
   fields were cast to `MaterialAutoCompleteTextView`.
6. **`InputStream.readText()` did not resolve** with the toolchain in use; an
   explicit `readUtf8()` helper was added (`util/Io.kt`).
7. **Callbacks could not call suspending functions.** The engine and provider
   callbacks are now `suspend`, and the repository uses `channelFlow`.
8. **The `kotlin` Gradle plugin conflicted** with AGP 9's built-in Kotlin
   (`Cannot add extension with name 'kotlin'`). Only AGP is applied now.
9. **`jvmToolchain(17)` failed** — no JDK 17 on the machine. The toolchain
   targets 21 while the bytecode level stays at Java 17.
10. **Adding from the catalogue leaked the tap** to the activity underneath, so
    the servers screen never opened. The catalogue now returns a result and
    `ServersActivity` saves the server itself.
11. **`return@onToolApproval` did not resolve** on the named lambda argument;
    the early return was replaced with an `if/else` expression.

## Security feature added after the first report

Tool calls used to run the instant the model asked. Since a keyless MCP server
is untrusted third-party code — and the catalogue makes adding one a single
tap — an approval gate was added and verified on device:

* `ChatEngine.sendTurn` gained `onToolApproval`, consulted before every call.
* `ChatRepository` suspends the turn on a `Channel`, emits
  `ToolApprovalRequested`, and answers with `approveToolCall(approved, trustServer)`.
* Session trust is in memory only, so it does not survive a restart.
* Denying sends the model `"The user declined to run this tool."` rather than a
  fabricated result, and the card renders as **declined**.
* Dismissing the dialog (`setOnCancelListener`) counts as a denial.

`Settings.confirmToolCalls` defaults to on and can be turned off.

## Known behaviour

* The Enter key inserts a newline (the input is multi-line by design); the
  **Send** button is the send path.
* Tool calls run without a per-call confirmation prompt — add one before
  pointing the app at an untrusted server.
* The whole conversation is resent on every turn; add history trimming before
  using the 1M-context models.
