#!/usr/bin/env python3
"""Extract every anonymous (no-auth) remote MCP endpoint from the awesome list and probe it."""

import concurrent.futures
import re
import sys
import pathlib

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from probe_remote_mcp import probe  # noqa: E402

SOURCE = pathlib.Path(
    r"C:\Users\ahmed\.local\share\opencode\tool-output\tool_15ff45bac001sjYJio4R46r7dF"
)

if not SOURCE.exists():
    print("awesome-remote-mcp-servers README not cached; re-fetch it first")
    sys.exit(1)

text = SOURCE.read_text(encoding="utf-8", errors="replace")
lines = text.splitlines()

entries = []
for i, line in enumerate(lines):
    m = re.match(r"^- \[([^\]]+)\]\((https?://[^)]+)\)\s+`(https?://[^`]+)`", line)
    if not m:
        continue
    name, homepage, url = m.group(1), m.group(2), m.group(3)
    # the auth badge is on one of the next lines
    badge = ""
    for j in range(i + 1, min(i + 4, len(lines))):
        b = re.search(r"(🔓|🔑|🔐)", lines[j])
        if b:
            badge = b.group(1)
            break
    entries.append((name, url, badge))

free = [e for e in entries if e[2] == "🔓"]
print(f"total entries: {len(entries)}   anonymous: {len(free)}\n", flush=True)

limit = int(sys.argv[1]) if len(sys.argv) > 1 else 60
free = free[:limit]

results = []
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    futures = {pool.submit(probe, n, u): (n, u) for n, u, _ in free}
    for future in concurrent.futures.as_completed(futures):
        results.append(future.result())

ok = sorted([r for r in results if r[2].startswith("OK")], key=lambda r: r[0].lower())
fail = sorted(
    [r for r in results if not r[2].startswith("OK")], key=lambda r: r[0].lower()
)

import json
import pathlib

json_path = pathlib.Path(__file__).parent / "free_mcp_probe.json"
json_path.write_text(
    json.dumps(
        {
            "total_entries": len(entries),
            "anonymous": len(free),
            "working": [
                {"name": n, "url": u, "status": s, "tools": t} for n, u, s, t, _ in ok
            ],
            "unreachable": [
                {"name": n, "url": u, "status": s} for n, u, s, _, _ in fail
            ],
        },
        indent=2,
        ensure_ascii=False,
    ),
    encoding="utf-8",
)
print(f"json -> {json_path}")

print(f"=== WORKING ({len(ok)}) ===")
for name, url, status, tools, _ in ok:
    print(f"  {name:<38} {url}")
    print(
        f"      {status}  tools: {', '.join(tools[:10])}{' …' if len(tools) > 10 else ''}"
    )

print(f"\n=== NOT REACHABLE WITHOUT CREDENTIALS ({len(fail)}) ===")
for name, url, status, _, _ in fail:
    print(f"  {name:<38} {status}")
