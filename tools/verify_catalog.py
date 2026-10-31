#!/usr/bin/env python3
"""
Probe every URL declared in FreeMcpCatalog.kt and report which ones answer.

Keeps the in-app catalogue honest: an entry is only listed if the endpoint
answers a real `initialize` + `tools/list` handshake right now.
"""

import io
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from probe_remote_mcp import probe  # noqa: E402

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

CATALOG = (
    pathlib.Path(__file__).parent
    / ".."
    / "app/src/main/java/com/mcpai/chat/mcp/FreeMcpCatalog.kt"
).resolve()


def parse_entries():
    text = CATALOG.read_text(encoding="utf-8")
    entries = []
    # Each entry is `Entry(` ... a closing `),` at the entry's own indentation.
    for block in re.findall(r"Entry\(\n(.*?)\n        \),\n", text, re.S):
        name = re.search(r'"((?:[^"\\]|\\.)*)"', block)
        url = re.search(r'"(https?://[^"]+)"', block)
        if name and url:
            entries.append((name.group(1), url.group(1)))
    return entries


def main():
    entries = parse_entries()
    if not entries:
        print("no entries parsed from FreeMcpCatalog.kt")
        return 1

    print(f"{len(entries)} catalogue entries\n")

    results = []
    for name, url in entries:
        _, _, status, tools, _ = probe(name, url)
        results.append((name, url, status, tools))
        ok = status.startswith("OK")
        print(f"{'OK ' if ok else '-- '}{name}")
        print(f"      {url}")
        print(f"      {status}")
        if ok and tools:
            print(f"      tools: {', '.join(tools[:8])}")
        sys.stdout.flush()

    bad = [r for r in results if not r[2].startswith("OK")]
    print(f"\n{len(results) - len(bad)}/{len(results)} answer a live handshake")
    if bad:
        print("\nNOT REACHABLE:")
        for name, url, status, _ in bad:
            print(f"  {name:<26} {url:<48} {status}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
