#!/usr/bin/env python3
"""
Minimal reference MCP server (stdio) for testing the stdio transport.

It speaks newline-delimited JSON-RPC 2.0 on stdin/stdout, which is the shape
the MCP specification calls "stdio":

    client -> {"jsonrpc":"2.0","id":1,"method":"tools/list"}
    server <- {"jsonrpc":"2.0","id":1,"result":{"tools":[...]}}

Notifications (messages without an id) are answered with nothing, as the
specification requires.

Run it by hand:

    python tools/stdio_mcp_server.py

or from the app / a test:

    StdioMcpTransport(listOf("python", "tools/stdio_mcp_server.py"))

On a phone the command has to be a binary that exists on the device — a
Python interpreter is only there when Termux (or similar) is installed.
"""

import json
import sys
import time

PROTOCOL_VERSION = "2024-11-05"

TOOLS = [
    {
        "name": "echo",
        "description": "Echo the given text back to the caller.",
        "inputSchema": {
            "type": "object",
            "properties": {"text": {"type": "string", "description": "Text to echo"}},
            "required": ["text"],
        },
    },
    {
        "name": "now",
        "description": "Return the current server time in ISO-8601 format.",
        "inputSchema": {"type": "object", "properties": {}},
    },
    {
        "name": "add",
        "description": "Add two numbers.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "a": {"type": "number"},
                "b": {"type": "number"},
            },
            "required": ["a", "b"],
        },
    },
]


def call_tool(name, arguments):
    if name == "echo":
        return [{"type": "text", "text": str(arguments.get("text", ""))}]
    if name == "now":
        return [{"type": "text", "text": time.strftime("%Y-%m-%dT%H:%M:%S%z")}]
    if name == "add":
        try:
            total = float(arguments.get("a", 0)) + float(arguments.get("b", 0))
            return [{"type": "text", "text": str(total)}]
        except (TypeError, ValueError):
            return [{"type": "text", "text": "add expects numeric a and b"}]
    raise KeyError(name)


def dispatch(method, params):
    if method == "initialize":
        return {
            "protocolVersion": PROTOCOL_VERSION,
            "capabilities": {"tools": {"listChanged": True}},
            "serverInfo": {"name": "stdio-reference-mcp-server", "version": "1.0.0"},
        }
    if method == "ping":
        return {}
    if method == "tools/list":
        return {"tools": TOOLS}
    if method == "tools/call":
        name = params.get("name")
        arguments = params.get("arguments") or {}
        return {"content": call_tool(name, arguments), "isError": False}
    raise KeyError(method)


def main():
    while True:
        line = sys.stdin.readline()
        if not line:
            break
        line = line.strip()
        if not line:
            continue
        try:
            message = json.loads(line)
        except ValueError:
            _write(
                {
                    "jsonrpc": "2.0",
                    "id": None,
                    "error": {"code": -32700, "message": "parse error"},
                }
            )
            continue

        msg_id = message.get("id")
        if msg_id is None:
            # A notification: no reply, by specification.
            continue

        try:
            result = dispatch(message.get("method"), message.get("params") or {})
            _write({"jsonrpc": "2.0", "id": msg_id, "result": result})
        except KeyError as err:
            _write(
                {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "error": {
                        "code": -32601,
                        "message": f"method not found: {err.args[0]}",
                    },
                }
            )
        except Exception as err:  # pragma: no cover
            _write(
                {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "error": {"code": -32603, "message": str(err)},
                }
            )


def _write(payload):
    sys.stdout.write(json.dumps(payload) + "\n")
    sys.stdout.flush()


if __name__ == "__main__":
    main()
