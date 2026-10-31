# McpAiChat — project context

**Updated:** 2026-10-31 · **State:** domain-agnostic MCP management shipped and verified on a real device

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
| SPKI certificate pinning for user-trusted domains                  | Implemented + unit tested; capture path wired, but see _Open work_             |
| Lazy connect, one retry on failure                                 | **Verified on device** — tools appeared on the first message                   |
| Per-type error dialogs (pin mismatch, server unavailable, unknown) | **Verified on device** for server-unavailable; the Re-trust path not exercised |
| Active server remembered across launches                           | Implemented, not verified on device                                            |
| Export / import of server settings as JSON, secrets excluded       | Implemented + unit tested; UI buttons not exercised on device                  |
| Encrypted settings (AES-256-GCM, Android Keystore)                 | **Verified on device** — settings survive restarts                             |
| Global LLM provider settings                                       | **Verified on device** — chat answered with the configured key                 |
| Capped replay history (default 40, adjustable 4–200)               | Implemented + unit tested; the slider not exercised on device                  |
| Catalogue of 33 keyless public MCP servers                         | **Verified on device** — entries connect and answer                            |

Test suite: **87 unit tests across 9 classes, 0 failures**. Pre-commit hook runs typecheck + tests on every commit.

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

## Open work

1. **A trusted server silently loses its pin when re-saved.** `ServerEditActivity` writes `spkiPin = null`, so editing a trusted server turns pinning off without saying so. Fix: preserve the stored pin unless the user re-trusts.
2. **113 paths are still untracked** while the repo tracks 46 files — most of the app's sources, layouts and resources have never been committed. The history is a record of the changes, not of the app.
3. **`README.md` is stale in four places**: it says cleartext HTTP is enabled, that the API key sits in plain `SharedPreferences`, that server URLs are `http://…`, and that the whole conversation is replayed to the provider. All four changed — history is now capped by `settings.historyLimit` (default 40).
4. **No automated device test.** Verification is a manual procedure; a device test would need an instrumentation target that does not exist yet.
5. **`stdio` transport** needs a local MCP binary, so it is only practical on a rooted device or inside Termux.

## How work moves here

```text
conversation → grilling rounds → spec → tracer-bullet tickets
            → implement each ticket test-first → code review → device verification
```

Each ticket shipped as its own commit with its tests. Reviews run on two axes — standards and spec — and report separately, so a change can pass one and fail the other without the other hiding it.
