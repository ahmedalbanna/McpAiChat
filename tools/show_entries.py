#!/usr/bin/env python3
"""Print the verified tool lists for a set of names from the probe results."""

import io
import json
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

WANT = [
    "Toolforte",
    "NPMScan",
    "TranscriptAPI",
    "Sunrise-Sunset.org",
    "Papers by Ouroboros",
    "KDAN PDF",
    "TheQRCode.io",
    "Webhook Toolkit",
    "Prompt Injection Scanner",
    "Toolrail",
    "ScrapingBee",
    "Firecrawl",
    "Context7",
    "DeepWiki",
    "Hugging Face",
    "agentsvc.io",
    "Suprsonic",
    "Squarespace Domain Search",
    "Ambee Weather",
    "CometChat Docs",
    "ADITUS Developer Docs",
    "Convex",
    "AI Tools Directory",
    "7Maps",
    "Agora",
    "Agent Nexus",
    "SkillTide",
    "Pushary",
    "Call Me",
    "Algolab Market Data",
    "WikiBrand",
    "ViewportWitness",
    "localScreenshot",
    "ProfessionalHeadshot",
    "Roomvana",
    "GoodSign",
    "Mailcheer",
    "Lium GPU Marketplace",
    "Store API",
]

data = json.load(
    open(r"C:\Users\ahmed\McpAiChat\tools\free_mcp_probe.json", encoding="utf-8")
)
working = data["working"]

by_name = {w["name"]: w for w in working}
by_url = {}
for w in working:
    by_url[w["url"].rstrip("/").lower()] = w


def find(name):
    if name in by_name:
        return by_name[name]
    for w in working:
        if name.lower() in w["name"].lower():
            return w
    return None


for name in WANT:
    entry = find(name)
    if entry is None:
        print(f"?? {name}  (not in the verified probe set)")
        continue
    tools = entry["tools"]
    print(f"{entry['name']}")
    print(f'    url     = "{entry["url"]}"')
    print(f'    status  = "{entry["status"].split("  ")[0]}"')
    print(f"    tools   = {tools[:12]}")
    print()
