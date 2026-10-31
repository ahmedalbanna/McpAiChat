#!/usr/bin/env python3
"""
Probe remote MCP endpoints and report which ones answer a real handshake.

For every URL it performs:
  1. POST initialize  (JSON-RPC 2.0, Accept: application/json, text/event-stream)
  2. POST tools/list
and prints the server name plus the first tool names.

Usage:
    python tools/probe_remote_mcp.py                 # probe the default list
    python tools/probe_remote_mcp.py <url> [<url>…]  # probe specific URLs
"""

import json
import sys
import urllib.error
import urllib.request

DEFAULT = [
    ("DeepWiki", "https://mcp.deepwiki.com/mcp"),
    ("Context7", "https://mcp.context7.com/mcp"),
    ("GitMCP", "https://gitmcp.io/mcp"),
    ("HuggingFace", "https://huggingface.co/mcp"),
    ("Sentry", "https://mcp.sentry.dev/mcp"),
    ("Grafana", "https://mcp.grafana.com/mcp"),
    ("Notion", "https://mcp.notion.com/mcp"),
    ("Linear", "https://mcp.linear.app/mcp"),
    ("Exa", "https://mcp.exa.ai/mcp"),
    ("Tavily", "https://mcp.tavily.com/mcp"),
    ("Cloudflare", "https://browser.mcp.cloudflare.com/mcp"),
    ("Figma", "https://mcp.figma.com/mcp"),
    ("Zapier", "https://mcp.zapier.com/api/mcp/mcp"),
    ("Firecrawl", "https://mcp.firecrawl.dev/v2/mcp"),
    ("Apify", "https://mcp.apify.com/mcp"),
    ("Pipedream", "https://mcp.pipedream.net/mcp"),
    ("Wikipedia", "https://mcp.wikimedia.org/mcp"),
    ("DuckDuckGo", "https://duckduckgo.mcp.dugugu.com/mcp"),
    ("Glama", "https://glama.ai/mcp/servers"),
    ("Smithery", "https://server.smithery.ai/mcp"),
    ("YouTube", "https://mcp.youtube.com/mcp"),
    ("Brave", "https://brave.mcp.app/mcp"),
    ("Perplexity", "https://mcp.perplexity.ai/mcp"),
    ("Polygon", "https://mcp.polygon.io/mcp"),
    ("Stripe", "https://mcp.stripe.com/mcp"),
    ("Shopify", "https://mcp.shopify.dev/mcp"),
    ("Cloudinary", "https://mcp.cloudinary.com/mcp"),
    ("Replicate", "https://mcp.replicate.com/mcp"),
    ("Airtable", "https://mcp.airtable.com/mcp"),
    ("Asana", "https://mcp.asana.com/mcp"),
    ("Intercom", "https://mcp.intercom.com/mcp"),
    ("Squarespace", "https://mcp.squarespace.com/mcp"),
    ("11x", "https://mcp.11x.ai/mcp"),
    ("Slack", "https://slack.com/mcp"),
    ("MCP Registry", "https://registry.modelcontextprotocol.io/mcp"),
    ("WayStation", "https://mcp.waystation.ai/mcp"),
    ("Poe", "https://poe.mcp.app/mcp"),
    ("Msty", "https://mcp.msty.app/mcp"),
]


def post(url, payload, session_id=None, timeout=20):
    body = json.dumps(payload).encode("utf-8")
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json, text/event-stream",
    }
    if session_id:
        headers["Mcp-Session-Id"] = session_id
    request = urllib.request.Request(url, data=body, headers=headers, method="POST")
    with urllib.request.urlopen(request, timeout=timeout) as response:
        raw = response.read().decode("utf-8", "replace")
        sid = response.headers.get("Mcp-Session-Id")
    if "text/event-stream" in (response.headers.get("Content-Type") or ""):
        for line in raw.splitlines():
            if line.startswith("data:"):
                raw = line[5:].strip()
                break
    return (json.loads(raw) if raw.strip() else {}), sid


def probe(name, url):
    try:
        init, sid = post(
            url,
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "initialize",
                "params": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {},
                    "clientInfo": {"name": "probe", "version": "1.0"},
                },
            },
        )
    except urllib.error.HTTPError as err:
        return name, url, f"HTTP {err.code}", [], None
    except Exception as err:
        return name, url, f"{type(err).__name__}: {err}", [], None

    if "error" in init:
        return name, url, f"rpc-error: {init['error'].get('message')}", [], None

    result = init.get("result") or {}
    server = result.get("serverInfo") or {}
    label = server.get("name") or "?"
    version = server.get("version") or "?"

    tools = []
    try:
        reply, _ = post(
            url, {"jsonrpc": "2.0", "id": 2, "method": "tools/list"}, session_id=sid
        )
        tools = [t.get("name") for t in (reply.get("result") or {}).get("tools", [])]
    except Exception:
        pass

    return name, url, f"OK {label} v{version}", tools, sid


def main():
    targets = DEFAULT
    if len(sys.argv) > 1:
        targets = [(u, u) for u in sys.argv[1:]]

    for name, url in targets:
        name, url, status, tools, _ = probe(name, url)
        marker = "OK " if status.startswith("OK") else "-- "
        print(f"{marker}{name:<16} {status}")
        if tools:
            shown = ", ".join(tools[:8])
            more = f" (+{len(tools) - 8} more)" if len(tools) > 8 else ""
            print(f"                 tools: {shown}{more}")
        sys.stdout.flush()


if __name__ == "__main__":
    main()
