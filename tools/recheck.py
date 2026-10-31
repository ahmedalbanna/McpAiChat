#!/usr/bin/env python3
"""Re-probe a few URLs several times to tell a real failure from transient rate-limiting."""

import io
import sys
import time

sys.path.insert(0, __file__.rsplit("\\", 1)[0])
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from probe_remote_mcp import probe  # noqa: E402

TARGETS = [
    ("NPMScan", "https://npmscan.com/api/mcp"),
    ("Toolforte", "https://toolforte.com/api/mcp"),
    ("Context7", "https://mcp.context7.com/mcp"),
    ("DeepWiki", "https://mcp.deepwiki.com/mcp"),
]

ROUNDS = 4
DELAY = 12


def main():
    for round_no in range(1, ROUNDS + 1):
        print(f"--- round {round_no} ---")
        for name, url in TARGETS:
            _, _, status, tools, _ = probe(name, url)
            verdict = "OK " if status.startswith("OK") else "FAIL"
            print(f"  {verdict} {name:<14} {status}")
            if tools:
                print(f"       {', '.join(tools[:6])}")
            sys.stdout.flush()
        if round_no < ROUNDS:
            time.sleep(DELAY)


if __name__ == "__main__":
    main()
