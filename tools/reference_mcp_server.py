#!/usr/bin/env python3
"""
Minimal reference MCP server (Streamable HTTP) for testing MCP AI Chat.

It implements the subset of the Model Context Protocol the app uses:
  - initialize / notifications/initialized
  - tools/list
  - tools/call
  - resources/list, resources/read
  - prompts/list, prompts/get
  - ping

Run:
    python tools/reference_mcp_server.py --port 8080

Then add a server in the app:
    name : local-tools
    url  : http://<your-lan-ip>:8080/mcp
  (from the Android emulator use http://10.0.2.2:8080/mcp)
"""

import argparse
import json
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

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
    {
        "name": "read_file",
        "description": "Read a UTF-8 text file from the server's allowed directory.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "path": {
                    "type": "string",
                    "description": "Absolute path of the file to read",
                }
            },
            "required": ["path"],
        },
    },
]

RESOURCES = [
    {
        "uri": "file:///README.txt",
        "name": "README",
        "description": "A tiny readme served as an MCP resource.",
        "mimeType": "text/plain",
    },
]

PROMPTS = [
    {
        "name": "summarize",
        "description": "Summarise the given text.",
        "arguments": [
            {"name": "text", "description": "Text to summarise", "required": True},
        ],
    },
]

README_TEXT = (
    "Hello from the reference MCP server.\nThis file is exposed as an MCP resource.\n"
)


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
    if name == "read_file":
        path = arguments.get("path", "")
        try:
            with open(path, "r", encoding="utf-8") as fh:
                return [{"type": "text", "text": fh.read()}]
        except OSError as err:
            return [{"type": "text", "text": f"cannot read {path}: {err}"}]
    raise KeyError(name)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("[mcp] " + fmt % args)

    def _read_json(self):
        length = int(self.headers.get("Content-Length") or 0)
        if not length:
            return None
        return json.loads(self.rfile.read(length).decode("utf-8"))

    def _send(self, payload, status=200, extra_headers=None):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        for key, value in (extra_headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if not self.path.rstrip("/").endswith("/mcp"):
            self._send({"error": "not found"}, 404)
            return
        try:
            message = self._read_json()
        except Exception as err:
            self._send({"error": str(err)}, 400)
            return
        if message is None:
            self._send({}, 202)
            return

        method = message.get("method")
        msg_id = message.get("id")
        params = message.get("params") or {}

        # Notifications carry no id and expect an empty 202.
        if msg_id is None:
            self._send({}, 202)
            return

        try:
            result = self._dispatch(method, params)
            self._send({"jsonrpc": "2.0", "id": msg_id, "result": result})
        except KeyError as err:
            self._send(
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
            self._send(
                {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "error": {"code": -32603, "message": str(err)},
                }
            )

    def _dispatch(self, method, params):
        if method == "initialize":
            return {
                "protocolVersion": PROTOCOL_VERSION,
                "capabilities": {
                    "tools": {"listChanged": True},
                    "resources": {"subscribe": True},
                    "prompts": {"listChanged": True},
                },
                "serverInfo": {"name": "reference-mcp-server", "version": "1.0.0"},
            }
        if method == "ping":
            return {}
        if method == "tools/list":
            return {"tools": TOOLS}
        if method == "tools/call":
            name = params.get("name")
            arguments = params.get("arguments") or {}
            content = call_tool(name, arguments)
            return {"content": content, "isError": False}
        if method == "resources/list":
            return {"resources": RESOURCES}
        if method == "resources/read":
            uri = params.get("uri")
            if uri == "file:///README.txt":
                return {
                    "contents": [
                        {"uri": uri, "mimeType": "text/plain", "text": README_TEXT}
                    ]
                }
            raise KeyError(f"unknown resource {uri}")
        if method == "prompts/list":
            return {"prompts": PROMPTS}
        if method == "prompts/get":
            name = params.get("name")
            if name != "summarize":
                raise KeyError(name)
            text = (params.get("arguments") or {}).get("text", "")
            return {
                "description": "Summarise the text",
                "messages": [
                    {
                        "role": "user",
                        "content": {"type": "text", "text": f"Summarise:\n{text}"},
                    }
                ],
            }
        raise KeyError(method)

    def do_GET(self):
        # A GET on /mcp would normally open the SSE stream; the app does not need it.
        self._send({"error": "GET not supported"}, 405)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--host", default="0.0.0.0")
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"[mcp] reference MCP server listening on http://{args.host}:{args.port}/mcp")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[mcp] stopped")


if __name__ == "__main__":
    main()
