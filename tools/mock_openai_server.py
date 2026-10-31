#!/usr/bin/env python3
"""
Mock OpenAI-compatible server used to test the MCP AI Chat app end to end.

It answers `/v1/chat/completions` with SSE. The first turn asks the model to
call the `add` tool; once the tool result comes back it produces a final
answer that quotes the tool output.

    python tools/mock_openai_server.py --port 8098
"""

import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def sse(chunks):
    for chunk in chunks:
        yield "data: " + json.dumps(chunk) + "\n\n"
    yield "data: [DONE]\n\n"


def delta_text(text):
    return {
        "id": "chatcmpl-mock",
        "object": "chat.completion.chunk",
        "created": int(time.time()),
        "model": "mock-model",
        "choices": [{"index": 0, "delta": {"content": text}, "finish_reason": None}],
    }


def delta_tool_call(call_id, name, arguments):
    return {
        "id": "chatcmpl-mock",
        "object": "chat.completion.chunk",
        "created": int(time.time()),
        "model": "mock-model",
        "choices": [
            {
                "index": 0,
                "delta": {
                    "role": "assistant",
                    "tool_calls": [
                        {
                            "index": 0,
                            "id": call_id,
                            "type": "function",
                            "function": {"name": name, "arguments": arguments},
                        }
                    ],
                },
                "finish_reason": None,
            }
        ],
    }


def delta_finish(reason):
    return {
        "id": "chatcmpl-mock",
        "object": "chat.completion.chunk",
        "created": int(time.time()),
        "model": "mock-model",
        "choices": [{"index": 0, "delta": {}, "finish_reason": reason}],
    }


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("[openai-mock] " + fmt % args)

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length).decode("utf-8") if length else "{}"
        try:
            body = json.loads(raw)
        except json.JSONDecodeError:
            body = {}

        messages = body.get("messages") or []
        tool_names = [
            t.get("function", {}).get("name") for t in (body.get("tools") or [])
        ]
        last_role = messages[-1].get("role") if messages else None

        print(f"[openai-mock] turn role={last_role} tools={tool_names}")
        print("[openai-mock] messages sent by the app:")
        for message in messages:
            summary = dict(message)
            if "content" in summary:
                summary["content"] = str(summary["content"])[:120]
            print("    " + json.dumps(summary)[:500])

        if last_role == "tool":
            result = messages[-1].get("content", "")
            chunks = [
                delta_text("The MCP tool answered: "),
                delta_text(result.strip()),
                delta_finish("stop"),
            ]
        elif tool_names:
            tool = "add" if "add" in tool_names else tool_names[0]
            chunks = [
                delta_tool_call("call_mock_1", tool, json.dumps({"a": 21, "b": 21})),
                delta_finish("tool_calls"),
            ]
        else:
            chunks = [
                delta_text("No MCP tools were offered to me, so I cannot call any."),
                delta_finish("stop"),
            ]

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        for frame in sse(chunks):
            self.wfile.write(frame.encode("utf-8"))
            self.wfile.flush()

    def do_GET(self):
        body = json.dumps(
            {"object": "list", "data": [{"id": "mock-model", "object": "model"}]}
        ).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8098)
    parser.add_argument("--host", default="0.0.0.0")
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"[openai-mock] listening on http://{args.host}:{args.port}/v1")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[openai-mock] stopped")


if __name__ == "__main__":
    main()
