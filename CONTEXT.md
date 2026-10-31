# McpAiChat — project context

**Updated:** 2026-10-31 · **State:** domain-agnostic MCP management shipped, verified on a real device, and covered by an automated device test

The briefing: what this app is, why it is shaped this way, where it stands, and what is left. Companion documents:

| Document                             | Answers                                                      |
| ------------------------------------ | ------------------------------------------------------------ |
| `AGENTS.md`                          | How to build and work in this repo                           |
| `GLOSSARY.md`                        | What the domain words mean                                   |
| `docs/spec/mcp-server-management.md` | What was asked for (41 requirements, 11 acceptance criteria) |
| `README.md`                          | How to install and use it                                    |

## Why it exists

An MCP client that is not welded to one provider or one server. The user names any MCP server by URL, decides how far to trust it, and the app keeps the model, the tools and the approvals in one place.

Two constraints shaped every decision:

- **No provider lock-in.** The LLM is any OpenAI-compatible endpoint (`api_key` / `base_url` / `model`, global to the app). Nothing about OpenAI is baked in, and no provider ships with the app.
- **An MCP server is arbitrary code the model can invoke.** So every tool call is approved by default, trust is earned per tool, and it is revocable at any time.

## What ships today

| Capability                                                         | State                                                                          |
| ------------------------------------------------------------------ | ------------------------------------------------------------------------------ |
| Any MCP server by URL + auth header, timeout, custom headers       | **Verified on device** — DeepWiki and Firecrawl connected, 6 tools listed      |
| Manual approval per tool call, with the owning server named        | **Verified on device** — dialog shown, `Allow` executed the call               |
| Persistent per-tool auto-approval, revocable                       | Implemented + unit tested; the toggle not exercised on device                  |
| SPKI certificate pinning for user-trusted domains                  | Implemented + unit tested + **device tested** — the pin survives an edit       |
| Lazy connect, one retry on failure                                 | **Verified on device** — tools appeared on the first message                   |
| Per-type error dialogs (pin mismatch, server unavailable, unknown) | **Verified on device** for server-unavailable; the Re-trust path not exercised |
| Active server remembered across launches                           | Implemented, not verified on device                                            |
| Export / import of server settings as JSON, secrets excluded       | Implemented + unit tested; UI buttons not exercised on device                  |
| Encrypted settings (AES-256-GCM, Android Keystore)                 | **Verified on device** — and by an instrumentation test that reads the file    |
| Global LLM provider settings                                       | **Verified on device** — chat answered with the configured key                 |
| Capped replay history (default 40, adjustable 4–120)               | Implemented + unit tested; the slider not exercised on device                  |
| Catalogue of 33 keyless public MCP servers                         | **Verified on device** — entries connect and answer                            |

Test suite: **101 unit tests across 12 classes + 13 instrumentation tests on the device, 0 failures**. The pre-commit hook runs typecheck + unit tests on every commit; `gradle app:connectedDebugAndroidTest` runs the device tests.

## How it fits together

```text
Settings (encrypted) ──▶ McpRegistry ──▶ McpClient ──▶ McpTransport ──▶ MCP server
       │                     │                              ▲
       │                     │ tool routing                 │
       │                     ▼                              │
       └──▶ ChatRepository ◀── ChatEngine ◀──▶ OpenAiProvider ──▶ LLM endpoint
                 │                ▲  │
                 │                │  └── approval gate ──▶ ToolApprovalStore
                 ▼                │
          ChatDatabase (SQLite: conversations, messages, tool_approvals)
```

One turn: the user sends text → `ChatEngine` asks the provider → the provider answers with `tool_calls` → the approval gate asks the user → `ChatRepository` routes the call through `McpRegistry` to the server that owns the tool → the result returns to the model → the final answer streams into the chat.

**Seams that make it testable:** every decision that could be wrong lives in a plain class (`SpkiPinVerifier`, `ConnectionRetryPolicy`, `ErrorClassifier`, `ServerUrlValidator`, `ServerConfigSerializer`) and every platform dependency sits behind an interface with a fake (`EncryptedStore`, `ServerConfigSerializer`, `ToolApprovalStore`). The app has no Android dependency in its logic tests.

## Product principles

Settled during five review rounds and recorded in the glossary and the spec:

1. The catalogue of public servers is **suggestions only** — the user copies an entry into their own list and owns it from then on.
2. A server is configured by URL plus whatever that server needs: auth header name and value, timeout, custom headers.
3. Pinning applies **only** to domains the user chose to trust. A changed certificate rejects the connection and offers **Re-trust** — never a silent update.
4. HTTPS for every remote server; plain HTTP only for loopback.
5. Approval is **manual by default**; auto-approval is per `(server, tool)`, persisted, and revocable.
6. The active server is the last one used, restored on launch; deleting it clears the selection; no servers at startup routes to the servers screen.
7. Connections are **lazy** — the first message opens them, and one retry follows a failure before the error surfaces.
8. `McpRegistry` is the single source of truth for connection state.
9. Errors are typed, and each type gets its own dialog with the right action.
10. Export includes secrets only when the user explicitly asks.

## Security model

- **Transport** — SPKI pin (SHA-256 of the SubjectPublicKeyInfo, `sha256:` + base64) per trusted server; a mismatch raises `SpkiPinMismatchException` before the response body is read.
- **Tools** — the approval dialog names the tool, its owning server and its arguments; denial is reported to the model as "did not run" so it can answer from what it knows.
- **Secrets** — server configs and the provider key live in `SharedPreferences` encrypted with AES-256-GCM under an Android Keystore key. The Keystore generates the IV; a caller-supplied one is rejected by API 26.
- **Network** — the platform network security config trusts the system CA store and permits cleartext only on loopback.

## Decisions worth remembering

- **No `gradlew` wrapper** — the machine has system Gradle only. The wrapper would make the repo reproducible; until then, the build command is `gradle app:assembleDebug`.
- **JDK 21 toolchain, Java 17 bytecode** — JDK 17 is not installed on this machine.
- **`org.json` is not mocked in JVM tests.** Production JSON sits behind `ServerConfigSerializer`; `org.json:json` is a test-only dependency.
- **`network_security_config.xml` has API 26 parser rules** — every `<domain-config>` needs its own `<trust-anchors>`, and `<debug-overrides>` may not nest a `<domain-config>`. A parse failure breaks **all** HTTPS in the app while `curl` on the device still works, which is what made this bug look like a network problem.
- **Device `curl` and the app's TLS are different stacks** (BoringSSL vs Conscrypt). Test them separately.
- **The `adb-shell` skill and `device-debugger` subagent exist** so device verification is a repeatable procedure rather than improvisation.
- **The backup rules name the preferences file.** `backup_rules.xml` and `data_extraction_rules.xml` exclude `mcp_ai_chat_encrypted.xml`; the name lives in `EncryptedPreferences.PREFERENCES_NAME` and `BackupRulesTest` fails if the two drift apart.
- **`androidTest` covers what the JVM cannot.** The Keystore, SQLite, the Android HTTP stack and the network security config are only exercised on the device - `gradle app:connectedDebugAndroidTest`.

## What was closed on 2026-10-31

| Was                                                  | Now                                                                                                                                                                                                                                     |
| ---------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| A trusted server silently lost its pin when re-saved | `SpkiPinPolicy` keeps the pin while the server stays trusted on the same host; the edit screen carries the custom headers and the enabled flag over too. Device tested.                                                                 |
| 113 paths untracked, 46 tracked                      | 201 files tracked; a fresh clone builds the APK and passes the tests.                                                                                                                                                                   |
| `README.md` stale in four places                     | Rewritten: HTTPS-only, encrypted settings, capped history, pinning, per-tool auto-approval, export/import.                                                                                                                              |
| No automated device test                             | `app/src/androidTest` with 13 tests, run with `gradle app:connectedDebugAndroidTest`.                                                                                                                                                   |
| `stdio` transport needed a local binary              | `tools/stdio_mcp_server.py` is the reference binary, and the transport is now covered by a test - which found two real defects (the child's stdin was closed after one message, and the transport waited for a reply to notifications). |

## Open work

- **The stdio transport is not reachable from the phone UI.** The app configures every server by URL, so `StdioMcpTransport` is exercised by the test harness and by desktop reuse only. On a phone it would also need a binary built for the device's ABI. Putting it in the app means a transport field in the server config and a command field in the edit screen - a spec change, not a patch.
- **The catalogue is a snapshot from 2026-10-29.** The public endpoints are third-party and can change or disappear; `python tools/verify_catalog.py` re-checks them.
- **Paths not exercised by hand on the device:** the per-tool auto-approve toggle, the export/import buttons, the active-server restore and the Re-trust flow.

## How work moves here

```text
conversation → grilling rounds → spec → tracer-bullet tickets
            → implement each ticket test-first → code review → device verification
```

Each ticket shipped as its own commit with its tests. Reviews run on two axes — standards and spec — and report separately, so a change can pass one and fail the other without the other hiding it.
