# McpAiChat — project instructions

Android client (Kotlin, `com.mcpai.chat`) for the Model Context Protocol: it connects to any number of user-configured MCP servers, exposes their tools to an OpenAI-compatible model, and runs the tool calls the model requests.

## Build and verify

- `gradle app:assembleDebug` — build the APK (this repo has **no `gradlew` wrapper**; use the system Gradle. JDK 21 toolchain, Java 17 bytecode).
- `gradle app:compileDebugKotlin` — typecheck.
- `gradle app:testDebugUnitTest` — unit tests; XML results land in `app/build/test-results/testDebugUnitTest/`.
- `gradle app:connectedDebugAndroidTest` — device tests on the attached phone; they cover the Keystore, SQLite, the HTTP stack and the network security config, none of which the JVM can reach. Results land in `app/build/reports/androidTests/connected/debug/`.
- The pre-commit hook (Husky) runs typecheck + tests; lint-staged runs Prettier over staged JSON/MD/XML/YAML.
- Stage source paths explicitly — `git add -A` fails on long filenames under `app/build/`.

## Where things live

| Concern                           | File                                                                                                                                                                                                                                                                                       |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Server config model               | `app/src/main/java/com/mcpai/chat/mcp/McpModels.kt`                                                                                                                                                                                                                                        |
| MCP client and transports         | `mcp/McpClient.kt`, `mcp/McpTransport.kt`, `mcp/HttpMcpTransport.kt`                                                                                                                                                                                                                       |
| stdio transport                   | `mcp/StdioMcpTransport.kt` (reference server: `tools/stdio_mcp_server.py`)                                                                                                                                                                                                                 |
| Connection state and tool routing | `mcp/McpRegistry.kt`                                                                                                                                                                                                                                                                       |
| Certificate pinning               | `mcp/SpkiPinVerifier.kt` (decision), `mcp/HttpMcpTransport.kt` (enforcement)                                                                                                                                                                                                               |
| Pin survival across edits         | `mcp/SpkiPinPolicy.kt`                                                                                                                                                                                                                                                                     |
| Retry policy                      | `mcp/ConnectionRetryPolicy.kt`                                                                                                                                                                                                                                                             |
| URL rules                         | `mcp/ServerUrlValidator.kt`                                                                                                                                                                                                                                                                |
| Encrypted settings                | `data/EncryptedPreferences.kt`, `data/Settings.kt`                                                                                                                                                                                                                                         |
| Server config (de)serialisation   | `data/ServerConfigSerializer.kt`                                                                                                                                                                                                                                                           |
| Tool approvals                    | `data/ToolApprovalStore.kt` (+ `tool_approvals` table in `data/ChatDatabase.kt`)                                                                                                                                                                                                           |
| Export / import                   | `data/SettingsExporter.kt`                                                                                                                                                                                                                                                                 |
| Conversation turn loop            | `engine/ChatEngine.kt`                                                                                                                                                                                                                                                                     |
| LLM provider                      | `llm/OpenAiProvider.kt`                                                                                                                                                                                                                                                                    |
| Error categories                  | `util/ChatError.kt` (`ErrorClassifier`)                                                                                                                                                                                                                                                    |
| Server and chat UI                | Compose screens: `ui/{ConversationList,Main}Screen.kt`, `ui/ServersScreen.kt`, `ui/CatalogScreen.kt`, `ui/ServerEditScreen.kt`, `ui/SettingsScreen.kt`, `ui/ToolDetailScreen.kt`, rows in `ui/ChatRows.kt`, approval in `ui/ToolApprovalDialog.kt`, shared alert in `ui/AppAlertDialog.kt` |
| Theme                             | `ui/theme/{Color,Theme,Type}.kt` — Material You on API 31+, brand fallback, `LocalChatColors` for bubble/composer/tool colors                                                                                                                                                              |
| Markdown                          | `util/Markdown.kt` — CommonMark 0.32.0 parser (GFM tables pre-parsed spec-plus); `ui/markdown/MarkdownContent.kt` — Compose renderer                                                                                                                                                       |
| Device-only tests                 | `app/src/androidTest/java/com/mcpai/chat/`                                                                                                                                                                                                                                                 |

## Conventions

- **Keep the decision JVM-testable.** Put it in a plain class (`SpkiPinVerifier`, `ConnectionRetryPolicy`, `ErrorClassifier`, `ServerUrlValidator`, `ServerConfigSerializer`) and let the Android layer call it. Interfaces with fake implementations (`EncryptedStore`, `ServerConfigSerializer`, `ToolApprovalStore`) are the seam that lets tests run without a device.
- **JSON in unit tests**: `org.json` is not mocked on the JVM. `org.json:json` is a test dependency, and production code that touches JSON stays behind an interface so a fake can replace it.
- **No provider lock-in**: LLM settings (`api_key`, `base_url`, `model`) are global for the app; MCP servers are per-user and domain-agnostic.
- **HTTPS only for remote servers**: plain HTTP is accepted for loopback only, and the app trusts the system CA store.
- **One owner per fact**: `McpRegistry` owns connection state, `Settings` owns server configs, `ToolApprovalStore` owns approvals.

## Platform rules that bite here

- `app/src/main/res/xml/network_security_config.xml` has API 26 parser rules — every `<domain-config>` needs its own `<trust-anchors>`, and `<debug-overrides>` may not nest a `<domain-config>`. A file that fails to parse breaks **all** HTTPS in the app, not just one server.
- The Android Keystore rejects a caller-supplied IV — let it generate one (`cipher.init(Cipher.ENCRYPT_MODE, key)`, then read `cipher.iv`).
- Only `ConversationListActivity` is exported; the remaining activities are reached by tapping.

## Device verification

- Skill `adb-shell` — build, install, drive the UI, read logs and screenshots. Load it before any device work.
- Subagent `device-debugger` — reproduces and verifies behaviour on the attached phone and reports evidence (contract in `.opencode/references/device-evidence.md`).

## Docs

- `CONTEXT.md` — project briefing: why the app is shaped this way, what ships today, what is left.
- `GLOSSARY.md` — canonical domain vocabulary.
- `docs/spec/mcp-server-management.md` — authoritative spec: requirements, data model, acceptance criteria.
- `FREE-MCP-SERVERS.md` — catalogue of suggested public MCP servers.
