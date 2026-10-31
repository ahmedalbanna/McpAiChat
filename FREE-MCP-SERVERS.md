# Free (no-key) remote MCP servers

Servers checked on **2026-10-29** against the app's own client code
(`HttpMcpTransport`, `Mcp-Protocol-Version: 2024-11-05`).

## How this list was produced

The `awesome-remote-mcp-servers` directory lists **1 281** remote MCP endpoints,
**790** of which are marked as needing no authentication. All 790 were probed
with a real `initialize` + `tools/list` handshake; **211** answered.

From those, **33** were kept for the in-app catalogue on the basis of being
generally useful to a chat client. Re-check any of them with:

```bash
python tools/verify_catalog.py          # parse FreeMcpCatalog.kt and probe it
python tools/probe_all_free_mcp.py 790   # re-run the whole directory probe
python tools/probe_remote_mcp.py <url>  # probe a single URL
```

`tools/free_mcp_probe.json` holds the full machine-readable result of the
directory probe.

## Status of the well-known vendors

Most commercial vendors require an OAuth key and reject an anonymous
`initialize`:

| Service | Result |
|---|---|
| Notion, Linear, Figma, Sentry, Grafana, Zapier, Exa, Tavily, Cloudflare, Smithery, Glama | `HTTP 401/403` — needs an account token |
| GitHub MCP (`api.githubcopilot.com/mcp`) | `HTTP 401` |
| Apify, Pipedream | endpoint not found at the advertised URL |
| Wikipedia (mcp.wikimedia.org), DuckDuckGo, YouTube, Brave, Perplexity | DNS does not resolve |

Only keyless endpoints are in the catalogue.

## The catalogue in the app

`app/src/main/java/com/mcpai/chat/mcp/FreeMcpCatalog.kt`

Every entry was probed on the day this file was written. Three entries that
were working during the directory probe had since added a paywall and were
removed rather than left as dead links:

| Removed | Why |
|---|---|
| NPMScan | now `HTTP 403` |
| Toolforte | now `HTTP 401` |
| Framejet | `HTTP 503` |

**Re-verify before shipping.** Public MCP servers come and go quickly; a
catalogue entry is a starting point, not a service guarantee. The app itself
reports the real status when you add a server, and the Servers screen shows
`Connected · N tools` or `Failed: <reason>`.

## Recommended starting set

| Server | Why |
|---|---|
| **DeepWiki** | Ask questions about any public GitHub repo — the single most useful one for code work |
| **Context7** | Current library docs, which the model's training data will not have |
| **Hugging Face** | Model and dataset search, plus reading repo files |
| **Firecrawl** or **ScrapingBee** | Read a web page the model needs to see |
| **agentsvc.io** | A grab-bag: web scrape, news search, translate, IP lookup, SEC filings |
| **TranscriptAPI** | YouTube transcripts |
| **Webhook Toolkit** | Create a webhook, then let the model *wait* on an external system |
| **Prompt Injection Scanner** | Screen text for injection patterns before trusting it |
| **7Maps / Agora / Agent Nexus / SkillTide** | Servers that find other servers, so the catalogue can be extended at runtime |

## A warning about untrusted servers

A keyless MCP server is **untrusted input**, on the same footing as a
prompt from a stranger. It can return tool descriptions and results that
instruct the model to exfiltrate the conversation, call other tools, or
change its own instructions.

The app currently executes a tool call the model requests without asking.
Before pointing it at servers you do not control, add the per-call
confirmation prompt (the top item in `README.md`'s "before production" list).

The catalogue deliberately includes the **Prompt Injection Scanner** so you
can screen returned text, but that is a mitigation, not a substitute for
the confirmation prompt.

---

*Last verified: 2026-10-29 — 33/33 catalogue entries answered.*
