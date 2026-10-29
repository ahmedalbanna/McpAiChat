# Specification: Domain-Agnostic MCP Server Management

**Status:** Draft (output of `/grill-with-docs` rounds 1–5)  
**Context:** MCP AI Chat — `com.mcpai.chat`  
**Source of truth:** `GLOSSARY.md` (repo root)  
**Related:** M1–M4 already implemented (MCP client, LLM provider, chat engine, storage, UI)

---

## 1. Overview

MCP AI Chat is an Android client that connects to **Model Context Protocol (MCP)** servers to execute tools and read resources through an LLM-driven tool loop.

Currently the app supports adding MCP servers, but the server configuration is rigid: it assumes specific providers, hard-coded trust assumptions, and manual per-call approval with no persistence.

This spec defines a **domain-agnostic, fully customizable** server management layer:
- Any MCP server URL can be added, edited, or deleted by the user.
- Each server carries its own dynamic configuration (auth headers, timeouts, custom headers).
- Trusted domains can opt into certificate pinning (SPKI hash).
- Tool approval is manual by default, but any tool can be opted into auto-approval and revoked at any time.
- Settings persist across app restarts.

The app must remain **free of hard-coded provider lock-in** — no domain, no API key, no MCP server is baked in as mandatory.

---

## 2. Goals & Non-Goals

### Goals
| ID | Goal |
|----|------|
| G1 | User can add, edit, and delete any MCP server via URL + dynamic fields. |
| G2 | Each server stores its own auth header, timeout, and optional custom headers. |
| G3 | Trusted domains may use SPKI certificate pinning; mismatch rejects the connection. |
| G4 | Tool approval is manual per-call by default; auto-approval is per-tool and persistent. |
| G5 | Last-used server loads on startup; if no server exists, user is prompted to add one. |
| G6 | Connection is lazy (established on first tool call), not permanent. |
| G7 | LLM provider settings (api_key, base_url, model) are global, set once. |
| G8 | All sensitive values (API keys, pins) are encrypted at rest via Android Keystore. |

### Non-Goals
| ID | Non-Goal |
|----|----------|
| NG1 | No hard-coded list of trusted providers. |
| NG2 | No automatic certificate trust rotation (user must re-trust manually). |
| NG3 | No cloud sync or cross-device backup of server settings. |
| NG4 | No built-in LLM provider; user supplies base_url + api_key. |
| NG5 | No persistent background WebSocket/SSE connection to MCP servers. |

---

## 3. Domain Model

Terms defined in `GLOSSARY.md` (single source of truth for vocabulary):

| Term | Definition |
|------|------------|
| **MCP Server** | A server offering tools and resources via MCP, added by the user via URL + config fields. |
| **Trusted Domain** | A domain the user marks as trusted; SPKI pin is applied. |
| **Tool Approval** | Per-tool decision: manual (default) or auto-approved; revocable at any time. |
| **Catalog** | Preloaded list of 33 public MCP servers as optional read-only suggestions. |
| **Server Config** | Per-server fields: URL, auth header name/value, timeout, custom headers, SPKI pin. |
| **SPKI Pin** | SHA-256 of SubjectPublicKeyInfo for a trusted domain; rejects mismatched certs. |
| **Default Auth Header** | `Authorization: Bearer <api_key>` preset for new servers; editable. |
| **Tool Auto-Approval** | Persisted in SQLite `tool_approvals`; survives app restart. |
| **Connection Loss Behavior** | Immediate failure with clear error; no retry queue or silent reconnect. |
| **Active Server on Startup** | Last-used server loaded automatically; stored in EncryptedPreferences. |
| **Lazy Connect** | Connect on first `tools/list` or `tools/call`; single retry on disconnect. |
| **Single Source of Truth** | `McpRegistry` holds connection state and active server selection. |
| **Settings Export/Import** | JSON export/import of server settings; sensitive keys excluded unless explicitly included. |
| **Error Dialog** | Specific dialog per error type: cert mismatch, tool approval required, server unavailable. |
| **Global LLM Settings** | One `api_key` / `base_url` / `model` for the whole app. |
| **Preloaded Catalog** | 33 public MCP servers bundled as suggestions, not defaults. |
| **Persistent Auto-Approval** | Auto-approval stored in SQLite; persists across restarts until revoked. |

---

## 4. Functional Requirements

### 4.1 Server Management

| ID | Requirement |
|----|-------------|
| FR-1 | The user can add a new MCP server by entering a URL. |
| FR-2 | Each new server gets default fields: `auth_header_name` = `Authorization`, `auth_header_value` = `Bearer <api_key>`, `request_timeout_seconds` = 30. |
| FR-3 | The user can edit any field of an existing server (URL, auth headers, timeout, custom headers, trusted domain flag). |
| FR-4 | The user can delete any server. Deleting the active server clears the active selection. |
| FR-5 | The user can view a list of all configured servers in `ServersActivity`. |
| FR-6 | Adding/editing a server validates that the URL is a valid HTTPS URL (or HTTP for debug builds only). |
| FR-7 | The preloaded catalog of 33 public MCP servers is available as optional suggestions; adding one copies it into the user's server list. |
| FR-8 | Catalog entries are read-only suggestions; they cannot be modified in-place, only copied. |

### 4.2 Certificate Pinning (Trusted Domains)

| ID | Requirement |
|----|-------------|
| FR-9 | The user can mark any server's domain as "trusted". |
| FR-10 | When a domain is first marked trusted, the app extracts the server's SPKI public-key hash (SHA-256 of SubjectPublicKeyInfo) and stores it with the server config. |
| FR-11 | On subsequent connections to a trusted domain, if the server's SPKI hash does not match the stored pin, the connection is rejected with an error dialog offering "Re-trust". |
| FR-12 | "Re-trust" re-extracts the current SPKI hash, updates the stored pin, and retries the connection. |
| FR-13 | Pinning is **not** applied to domains the user has not marked as trusted. |
| FR-14 | Debug builds may bypass pinning for local IPs (127.0.0.1, 10.0.2.2, 192.168.8.21) per existing `network_security_config.xml`. |

### 4.3 Authentication

| ID | Requirement |
|----|-------------|
| FR-15 | New servers default to auth header `Authorization: Bearer <api_key>`. |
| FR-16 | The user can change the auth header name and value per server. |
| FR-17 | The user can clear auth header name/value for servers that require no authentication. |
| FR-18 | All auth values are stored in `EncryptedPreferences` (Android Keystore, AES-256-GCM). |

### 4.4 Tool Approval

| ID | Requirement |
|----|-------------|
| FR-19 | Every tool call from any MCP server requires explicit user approval by default. |
| FR-20 | The approval dialog shows: server name, tool name, and tool arguments. |
| FR-21 | The user can mark any tool as "auto-approve" for a specific server. |
| FR-22 | Auto-approval is persisted in SQLite table `tool_approvals(server_id, tool_name, is_auto_approved)`. |
| FR-23 | Auto-approval survives app restart. |
| FR-24 | The user can revoke auto-approval for any tool at any time (toggle off in tool management screen). |
| FR-25 | Tool management screen lists all tools per server with an auto-approve toggle per tool. |

### 4.5 Connection & Lifecycle

| ID | Requirement |
|----|-------------|
| FR-26 | On app startup, the last-used server is loaded automatically (stored in EncryptedPreferences). |
| FR-27 | If no server is configured, the app shows an "Add server first" screen before opening chat. |
| FR-28 | Connection to an MCP server is **lazy**: established on first `tools/list` or `tools/call`, not at app open. |
| FR-29 | On connection loss, the app attempts one automatic reconnect on the next request; if it fails, it shows a clear error with "Retry". |
| FR-30 | No persistent background connection to any MCP server. |
| FR-31 | `McpRegistry` is the single source of truth for connected servers and active server selection. |

### 4.6 LLM Provider Settings

| ID | Requirement |
|----|-------------|
| FR-32 | LLM settings (`api_key`, `base_url`, `model`) are global — one set for the entire app. |
| FR-33 | LLM settings are configured once in `SettingsActivity` and applied to all servers/conversations. |
| FR-34 | LLM settings are stored in `EncryptedPreferences`. |

### 4.7 Error Handling

| ID | Requirement |
|----|-------------|
| FR-35 | SPKI mismatch shows a dedicated error dialog with "Re-trust" action. |
| FR-36 | Tool requiring approval shows a dedicated dialog with "Approve once" and "Approve automatically" actions. |
| FR-37 | Server unavailable shows a dedicated error dialog with "Retry" action. |
| FR-38 | All error dialogs include the server name for context. |

### 4.8 Settings Export/Import

| ID | Requirement |
|----|-------------|
| FR-39 | The user can export server settings (URL, headers, timeout, trusted flags, tool approvals) to a JSON file. |
| FR-40 | The user can import server settings from a JSON file. |
| FR-41 | Sensitive values (API keys, auth values) are excluded from export unless the user explicitly opts in. |

---

## 5. Non-Functional Requirements

| ID | Requirement |
|----|-------------|
| NFR-1 | All network connections to production domains use HTTPS (`usesCleartextTraffic="false"`). |
| NFR-2 | Sensitive data (API keys, auth headers, SPKI pins) is encrypted at rest via Android Keystore. |
| NFR-3 | Backup rules exclude `mcp_ai_chat.xml` from cloud backup and device transfer. |
| NFR-4 | Server list operations (add/edit/delete) complete in < 500ms on device. |
| NFR-5 | The app targets minSdk 26, compileSdk 36, Java 17 bytecode. |
| NFR-6 | No third-party runtime dependencies beyond AndroidX + Material + coroutines + org.json. |

---

## 6. Architecture & Design

### 6.1 Layers (unchanged from M1–M4)

| Layer | Responsibility | Key Files |
|-------|--------------|-----------|
| `mcp/` | Protocol client, transports, multi-server registry | `McpClient.kt`, `HttpMcpTransport.kt`, `StdioMcpTransport.kt`, `McpRegistry.kt`, `McpModels.kt` |
| `llm/` | OpenAI-compatible streaming provider | `OpenAiProvider.kt`, `LlmModels.kt` |
| `engine/` | LLM ↔ MCP tool loop with approval gate | `ChatEngine.kt` |
| `data/` | Config, SQLite, encryption | `Settings.kt`, `ChatDatabase.kt`, `ChatRepository.kt`, `EncryptedPreferences.kt`, `TurnHistory.kt` |
| `ui/` | Activities, adapters, dialogs | `MainActivity.kt`, `ServersActivity.kt`, `ServerEditActivity.kt`, `SettingsActivity.kt`, `CatalogActivity.kt` |

### 6.2 New / Modified Components

| Component | Change |
|-----------|--------|
| `ServerEditActivity` | Add fields: URL, auth header name, auth header value, request timeout, custom headers (JSON), trusted domain toggle. |
| `ServersActivity` | Add "Add server" FAB; list shows server name/URL + trusted badge. |
| `EncryptedPreferences` | Store server configs (map of server_id → config JSON), active server id, LLM settings. |
| `ChatDatabase` | New table `tool_approvals(server_id TEXT, tool_name TEXT, is_auto_approved INTEGER)`. |
| `McpRegistry` | Already single source of truth; add trusted-domain SPKI pin lookup before connection. |
| `HttpMcpTransport` | On connect to trusted domain, verify server cert SPKI hash against stored pin; reject on mismatch. |

### 6.3 Data Model

**EncryptedPreferences** (key → encrypted value):
- `servers` — JSON map: `server_id` → `{name, url, auth_header_name, auth_header_value, timeout_seconds, custom_headers, is_trusted, spki_pin}`
- `active_server_id` — string
- `llm_api_key`, `llm_base_url`, `llm_model` — strings

**SQLite `tool_approvals` table:**
```sql
CREATE TABLE tool_approvals (
  server_id TEXT NOT NULL,
  tool_name TEXT NOT NULL,
  is_auto_approved INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (server_id, tool_name)
);
```

### 6.4 User Flows

1. **First launch (no servers):** App shows "Add server first" → user enters URL → defaults applied → server saved → chat opens.
2. **Subsequent launch:** Last-used server loads → chat opens → lazy connect on first tool call.
3. **Add server from catalog:** User browses catalog → taps a server → copied to user's list with defaults → editable.
4. **Mark domain trusted:** User toggles "trusted" on server → app connects → extracts SPKI pin → stores it.
5. **Cert mismatch:** App rejects connection → error dialog "Re-trust" → user confirms → pin updated → reconnect.
6. **Tool call:** LLM requests tool → approval dialog (manual default) → user approves → tool executes → result returns to LLM.
7. **Auto-approve tool:** User toggles auto-approve on tool management screen → persisted in SQLite → future calls skip approval.
8. **Export settings:** User taps export → JSON file created (sensitive keys excluded unless opted in) → share sheet.

---

## 7. Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC-1 | User can add a new MCP server with URL + auth fields; it appears in the server list. |
| AC-2 | User can edit a server's URL, auth header, timeout, and custom headers; changes persist. |
| AC-3 | User can delete a server; it is removed from the list and active selection is cleared if it was active. |
| AC-4 | Marking a domain trusted stores its SPKI pin; connecting to a server with a changed cert is rejected until user re-trusts. |
| AC-5 | Tool approval dialog shows server name, tool name, and args; manual approval is required by default. |
| AC-6 | Toggling auto-approve on a tool persists across app restart; future calls for that tool skip approval. |
| AC-7 | On startup, last-used server loads automatically; if no servers exist, "Add server first" screen is shown. |
| AC-8 | Connection is lazy: no network call to MCP server occurs at app open; first call happens on first tool use. |
| AC-9 | LLM settings (api_key, base_url, model) are global and applied to all servers. |
| AC-10 | Export produces a JSON file; API keys are excluded unless user explicitly includes them. |
| AC-11 | All sensitive values are encrypted via Android Keystore; not stored in plain-text SharedPreferences. |

---

## 8. Deferred / Out of Scope (for this spec)

- Certificate pinning with user-provided custom CA certificates (future).
- Cloud sync / cross-device backup of server settings.
- Per-server LLM settings (LLM settings remain global).
- Automatic certificate rotation (manual re-trust only).
- Background persistent connections to MCP servers.

---

## 9. Implementation Order (Suggested Tickets)

1. **Ticket 1:** Extend `ServerEditActivity` with URL + dynamic fields (auth, timeout, custom headers, trusted toggle).
2. **Ticket 2:** Add "Add server" FAB and server list improvements in `ServersActivity`.
3. **Ticket 3:** Store server configs in `EncryptedPreferences`; active server persistence.
4. **Ticket 4:** SPKI pin extraction and verification in `HttpMcpTransport` for trusted domains.
5. **Ticket 5:** `tool_approvals` SQLite table + tool management UI with auto-approve toggle.
6. **Ticket 6:** Lazy connect logic + single retry on disconnect in `McpRegistry`/`HttpMcpTransport`.
7. **Ticket 7:** Error dialogs per error type (cert mismatch, tool approval, server unavailable).
8. **Ticket 8:** Settings export/import to JSON with sensitive key exclusion.

Each ticket should be worked test-first via `/tdd`, then reviewed via `/code-review`.

---

**End of spec.** Ready for `/to-tickets` to split into tracer-bullet tickets with blocking edges.