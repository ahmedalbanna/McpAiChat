#!/usr/bin/env python3
"""Report the working servers from the probe that match a list of mainstream names."""

import io
import json
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

DATA = r"C:\Users\ahmed\McpAiChat\tools\free_mcp_probe.json"

KNOWN = [
    "deepwiki",
    "context7",
    "huggingface",
    "hugging face",
    "firecrawl",
    "exa",
    "tavily",
    "apify",
    "smithery",
    "notion",
    "linear",
    "figma",
    "sentry",
    "grafana",
    "zapier",
    "github",
    "gitlab",
    "jira",
    "confluence",
    "brave",
    "perplexity",
    "slack",
    "discord",
    "twitter",
    "youtube",
    "reddit",
    "stack",
    "npm",
    "pypi",
    "docker",
    "aws",
    "azure",
    "stripe",
    "paypal",
    "shopify",
    "twilio",
    "sendgrid",
    "airtable",
    "asana",
    "trello",
    "monday",
    "clickup",
    "wikipedia",
    "wikidata",
    "openstreetmap",
    "weather",
    "calendar",
    "gmail",
    "outlook",
    "drive",
    "dropbox",
    "box",
    "evernote",
    "spotify",
    "tmdb",
    "omdb",
    "wolfram",
    "arxiv",
    "pubmed",
    "semantic",
    "scholar",
    "crossref",
    "openalex",
    "news",
    "rss",
    "hackernews",
    "producthunt",
    "crunchbase",
    "polymarket",
    "coinbase",
    "binance",
    "coingecko",
    "defillama",
    "etherscan",
    "quicknode",
    "alchemy",
    "infura",
    "math",
    "search",
    "scrape",
    "screenshot",
    "web",
    "browse",
    "crawl",
    "registry",
    "directory",
    "mcp",
    "translator",
    "translate",
    "currency",
    "exchange",
    "geo",
    "map",
    "pdf",
    "image",
    "tts",
    "stt",
    "speech",
]


def main():
    data = json.load(open(DATA, encoding="utf-8"))
    working = data["working"]
    print(
        f"probed {data['total_entries']} entries, {data['anonymous']} anonymous, "
        f"{len(working)} answer a live handshake\n"
    )

    hits = [
        x
        for x in working
        if any(k in x["name"].lower() or k in x["url"].lower() for k in KNOWN)
    ]
    print(f"matching mainstream / capability names: {len(hits)}\n")

    for x in hits:
        status = x["status"].split("  ")[0]
        tools = ", ".join(x["tools"][:6])
        print(f"  {x['name'][:38]:<38} {x['url'][:54]:<54}")
        print(f"      {status[:56]}")
        if tools:
            print(f"      {tools[:100]}")


if __name__ == "__main__":
    main()
