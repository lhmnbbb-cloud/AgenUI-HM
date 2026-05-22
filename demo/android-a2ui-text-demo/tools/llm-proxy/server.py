#!/usr/bin/env python3
"""
LLM Proxy Server for A2UI Text Demo.

Receives HTTP POST from the Android app, calls a real LLM API
with a prompt that requests A2UI v0.9 JSON, and returns the result.

Supports both OpenAI and Anthropic API formats (set LLM_API_FORMAT).

Usage:
  # Anthropic format
  set LLM_API_KEY=your-api-key
  set LLM_BASE_URL=https://your-llm-endpoint.example.com
  set LLM_MODEL=your-model-name
  set LLM_API_FORMAT=anthropic
  python server.py

  # OpenAI format
  set LLM_API_KEY=your-api-key
  set LLM_BASE_URL=https://api.openai.com
  set LLM_MODEL=gpt-4o-mini
  set LLM_API_FORMAT=openai
  python server.py

  # Optional: bind to all interfaces for real-device testing (default: 127.0.0.1)
  set LLM_PROXY_HOST=0.0.0.0

  # Optional: require a proxy token for access control
  set LLM_PROXY_TOKEN=your-secret-token

The Android emulator accesses this via http://10.0.2.2:8787/generate-ui
Real devices use http://<your-pc-ip>:8787/generate-ui
"""

import json
import os
import sys
import urllib.request
import urllib.error
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn

LLM_API_KEY = os.environ.get("LLM_API_KEY", "")
LLM_BASE_URL = os.environ.get("LLM_BASE_URL", "")
LLM_MODEL = os.environ.get("LLM_MODEL", "")
LLM_API_FORMAT = os.environ.get("LLM_API_FORMAT", "anthropic").lower()
LLM_PROXY_HOST = os.environ.get("LLM_PROXY_HOST", "127.0.0.1")
LLM_PROXY_TOKEN = os.environ.get("LLM_PROXY_TOKEN", "")
PORT = int(os.environ.get("LLM_PROXY_PORT", "8787"))
LLM_REQUEST_TIMEOUT = int(os.environ.get("LLM_REQUEST_TIMEOUT", "100"))

# 22 built-in SDK components (Markdown/Lottie/Chart are custom, not registered in this demo)
SYSTEM_PROMPT = """You are an A2UI JSON generator. Given a user request, generate a valid A2UI v0.9 protocol response.

A2UI v0.9 protocol requires exactly 3 messages in a JSON array:
1. createSurface message
2. updateComponents message
3. updateDataModel message (can be empty {})

Format:
[
  {"version": "v0.9", "createSurface": {"surfaceId": "<unique-id>", "catalogId": "https://a2ui.org/specification/v0_9/standard_catalog.json"}},
  {"version": "v0.9", "updateComponents": {"surfaceId": "<same-id>", "components": [<component objects>]}},
  {}
]

Available component types: Text, Image, Icon, Video, AudioPlayer, Row, Column, List, Card, Tabs, Modal, Divider, Button, TextField, CheckBox, ChoicePicker, Slider, DateTimeInput, RichText, Table, Web, Carousel

Rules:
- Each component MUST have "id" and "component" fields
- Container components (Row, Column, List, Card, Tabs, Modal, Carousel) use "children" (array of child IDs) or "child" (single child ID)
- Leaf components (Text, Image, Icon, Video, AudioPlayer, Button, TextField, CheckBox, ChoicePicker, Slider, DateTimeInput, RichText, Table, Web, Divider) have content fields like "text", "label", "placeholder"
- Use "styles" object for styling (padding, gap, background-color, color, font-weight, text-align, border-radius, width, height, filter)
- Button can have "action": {"functionCall": {"call": "toast", "args": {"value": "message"}}}
- Return ONLY the JSON array, no markdown fences, no explanation
- surfaceId must be the same in createSurface and updateComponents"""


class ProxyHandler(BaseHTTPRequestHandler):

    def _send_json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _check_token(self):
        if not LLM_PROXY_TOKEN:
            return True
        token = self.headers.get("X-Proxy-Token", "")
        return token == LLM_PROXY_TOKEN

    def do_POST(self):
        if self.path == "/generate-ui-stream":
            self._handle_stream()
            return
        if self.path != "/generate-ui":
            self.send_error(404, "Not found")
            return

        if not self._check_token():
            self._send_json(403, {"error": "Invalid or missing X-Proxy-Token"})
            return

        if not LLM_API_KEY:
            self._send_json(500, {"error": "LLM_API_KEY not set. Set it as an environment variable."})
            return

        if not LLM_BASE_URL:
            self._send_json(500, {"error": "LLM_BASE_URL not set. Set it as an environment variable."})
            return

        if not LLM_MODEL:
            self._send_json(500, {"error": "LLM_MODEL not set. Set it as an environment variable."})
            return

        try:
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length).decode("utf-8")
            request = json.loads(body)
        except Exception as e:
            self._send_json(400, {"error": f"Invalid request body: {e}"})
            return

        user_input = request.get("userInput", "")
        available_components = request.get("availableComponents", [])

        if not user_input.strip():
            self._send_json(400, {"error": "userInput is required"})
            return

        try:
            messages = [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": f"Generate A2UI JSON for: {user_input}\n\nAvailable components: {', '.join(available_components)}"}
            ]

            if LLM_API_FORMAT == "anthropic":
                llm_response = call_llm_anthropic(messages)
            else:
                llm_response = call_llm_openai(messages)

            parsed = parse_llm_response(llm_response)

            self._send_json(200, {"messages": parsed})
            print(f"[OK] userInput='{user_input[:50]}' -> {len(json.dumps(parsed))} chars")

        except Exception as e:
            print(f"[ERROR] {e}")
            self._send_json(502, {"error": f"LLM call failed: {e}"})

    def _handle_stream(self):
        if not self._check_token():
            self._send_json(403, {"error": "Invalid or missing X-Proxy-Token"})
            return

        if not LLM_API_KEY:
            self._send_json(500, {"error": "LLM_API_KEY not set"})
            return
        if not LLM_BASE_URL:
            self._send_json(500, {"error": "LLM_BASE_URL not set"})
            return
        if not LLM_MODEL:
            self._send_json(500, {"error": "LLM_MODEL not set"})
            return

        try:
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length).decode("utf-8")
            request = json.loads(body)
        except Exception as e:
            self._send_json(400, {"error": f"Invalid request body: {e}"})
            return

        user_input = request.get("userInput", "")
        available_components = request.get("availableComponents", [])

        if not user_input.strip():
            self._send_json(400, {"error": "userInput is required"})
            return

        # Send SSE headers
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()

        def write_sse(text):
            for line in text.split("\n"):
                self.wfile.write(f"data: {line}\n".encode("utf-8"))
            self.wfile.write("\n".encode("utf-8"))
            self.wfile.flush()

        try:
            messages = [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": f"Generate A2UI JSON for: {user_input}\n\nAvailable components: {', '.join(available_components)}"}
            ]

            # Strip JSON array wrapper so SDK only receives {…} objects
            filtered_write = JsonArrayStripper(write_sse)

            if LLM_API_FORMAT == "anthropic":
                call_llm_anthropic_stream(messages, filtered_write)
            else:
                call_llm_openai_stream(messages, filtered_write)

            write_sse("[DONE]")
            print(f"[OK-Stream] userInput='{user_input[:50]}'")

        except Exception as e:
            print(f"[ERROR-Stream] {e}")
            try:
                write_sse(json.dumps({"error": str(e)}))
                write_sse("[DONE]")
            except Exception:
                pass


def call_llm_anthropic_stream(messages, write_fn):
    url = f"{LLM_BASE_URL.rstrip('/')}/v1/messages"

    system_text = ""
    api_messages = []
    for msg in messages:
        if msg["role"] == "system":
            system_text = msg["content"]
        else:
            api_messages.append(msg)

    payload = json.dumps({
        "model": LLM_MODEL,
        "max_tokens": 4096,
        "stream": True,
        "system": system_text,
        "messages": api_messages,
    }).encode("utf-8")

    req = urllib.request.Request(url, data=payload, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("x-api-key", LLM_API_KEY)
    req.add_header("Authorization", f"Bearer {LLM_API_KEY}")
    req.add_header("anthropic-version", "2023-06-01")

    try:
        with urllib.request.urlopen(req, timeout=LLM_REQUEST_TIMEOUT) as resp:
            buffer = ""
            for raw_line in resp:
                line = raw_line.decode("utf-8", errors="replace").strip()
                if not line:
                    continue
                if line.startswith("event:"):
                    buffer = line
                    continue
                if line.startswith("data:"):
                    data_str = line[5:].strip()
                    if buffer:
                        event_type = buffer.split(":", 1)[1].strip()
                        buffer = ""
                    else:
                        event_type = ""

                    if event_type == "message_stop":
                        return

                    if data_str == "[DONE]" or event_type in ("ping", "message_start", "message_delta"):
                        continue

                    try:
                        data_obj = json.loads(data_str)
                        if event_type == "content_block_delta":
                            delta = data_obj.get("delta", {})
                            if delta.get("type") == "text_delta":
                                text = delta.get("text", "")
                                if text:
                                    write_fn(text)
                    except json.JSONDecodeError:
                        pass
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"LLM API HTTP {e.code}: {error_body[:500]}")


def call_llm_openai_stream(messages, write_fn):
    url = f"{LLM_BASE_URL.rstrip('/')}/v1/chat/completions"
    payload = json.dumps({
        "model": LLM_MODEL,
        "messages": messages,
        "temperature": 0.7,
        "max_tokens": 4096,
        "stream": True,
    }).encode("utf-8")

    req = urllib.request.Request(url, data=payload, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", f"Bearer {LLM_API_KEY}")

    try:
        with urllib.request.urlopen(req, timeout=LLM_REQUEST_TIMEOUT) as resp:
            for raw_line in resp:
                line = raw_line.decode("utf-8", errors="replace").strip()
                if not line:
                    continue
                if not line.startswith("data:"):
                    continue
                data_str = line[5:].strip()
                if data_str == "[DONE]":
                    return
                try:
                    data_obj = json.loads(data_str)
                    choices = data_obj.get("choices", [])
                    if choices:
                        delta = choices[0].get("delta", {})
                        content = delta.get("content", "")
                        if content:
                            write_fn(content)
                except json.JSONDecodeError:
                    pass
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"LLM API HTTP {e.code}: {error_body[:500]}")


def call_llm_anthropic(messages):
    url = f"{LLM_BASE_URL.rstrip('/')}/v1/messages"

    system_text = ""
    api_messages = []
    for msg in messages:
        if msg["role"] == "system":
            system_text = msg["content"]
        else:
            api_messages.append(msg)

    payload = json.dumps({
        "model": LLM_MODEL,
        "max_tokens": 4096,
        "system": system_text,
        "messages": api_messages,
    }).encode("utf-8")

    req = urllib.request.Request(url, data=payload, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("x-api-key", LLM_API_KEY)
    req.add_header("Authorization", f"Bearer {LLM_API_KEY}")
    req.add_header("anthropic-version", "2023-06-01")

    try:
        with urllib.request.urlopen(req, timeout=LLM_REQUEST_TIMEOUT) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            for block in data["content"]:
                if block.get("type") == "text":
                    return block["text"]
            raise RuntimeError(f"No text block in LLM response, got types: {[b.get('type') for b in data['content']]}")
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"LLM API HTTP {e.code}: {error_body[:500]}")


def call_llm_openai(messages):
    url = f"{LLM_BASE_URL.rstrip('/')}/v1/chat/completions"
    payload = json.dumps({
        "model": LLM_MODEL,
        "messages": messages,
        "temperature": 0.7,
        "max_tokens": 4096,
    }).encode("utf-8")

    req = urllib.request.Request(url, data=payload, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", f"Bearer {LLM_API_KEY}")

    try:
        with urllib.request.urlopen(req, timeout=LLM_REQUEST_TIMEOUT) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return data["choices"][0]["message"]["content"]
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"LLM API HTTP {e.code}: {error_body[:500]}")


class JsonArrayStripper:
    """Strips JSON array wrapper ([, ], commas) from streaming text deltas,
    forwarding only individual JSON object content to the SSE write function.

    The AGenUI SDK's ProtocolStreamExtractor only recognizes '{' as a JSON
    object start character. When the LLM outputs a JSON array like
    [obj1, obj2, obj3], the leading '[' causes the parser to fail entirely.
    This stripper removes top-level array delimiters so only {…} objects
    reach the client, matching what the non-streaming path already does
    (parse then send individual messages).
    """

    def __init__(self, write_fn):
        self._write_fn = write_fn
        self._brace_depth = 0
        self._in_string = False
        self._escape_next = False

    def __call__(self, text):
        output = []
        for ch in text:
            if self._escape_next:
                output.append(ch)
                self._escape_next = False
                continue
            if ch == '\\' and self._in_string:
                output.append(ch)
                self._escape_next = True
                continue
            if ch == '"':
                self._in_string = not self._in_string
                output.append(ch)
                continue
            if self._in_string:
                output.append(ch)
                continue
            # Outside strings
            if ch == '{':
                self._brace_depth += 1
                output.append(ch)
            elif ch == '}':
                self._brace_depth -= 1
                output.append(ch)
            elif self._brace_depth > 0:
                output.append(ch)
            # else: top-level (brace_depth == 0) — skip [, ], commas, whitespace
        text_out = ''.join(output)
        if text_out.strip():
            self._write_fn(text_out)


def parse_llm_response(text):
    text = text.strip()
    if text.startswith("```"):
        lines = text.split("\n")
        lines = [l for l in lines if not l.strip().startswith("```")]
        text = "\n".join(lines).strip()

    parsed = json.loads(text)

    if not isinstance(parsed, list):
        raise RuntimeError(f"Expected JSON array, got {type(parsed).__name__}")

    result = []
    for i in range(3):
        if i < len(parsed):
            result.append(parsed[i])
        else:
            result.append({})

    return result


class ThreadingHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True


def main():
    if LLM_API_FORMAT not in ("anthropic", "openai"):
        print(f"ERROR: LLM_API_FORMAT must be 'anthropic' or 'openai', got '{LLM_API_FORMAT}'")
        sys.exit(1)

    missing = []
    if not LLM_API_KEY:
        missing.append("LLM_API_KEY")
    if not LLM_BASE_URL:
        missing.append("LLM_BASE_URL")
    if not LLM_MODEL:
        missing.append("LLM_MODEL")
    if missing:
        print(f"WARNING: {', '.join(missing)} not set. The server will return errors.")
        print("  Example: set LLM_API_KEY=your-key")
        print()

    print(f"A2UI LLM Proxy Server")
    print(f"  Endpoint:   http://{LLM_PROXY_HOST}:{PORT}/generate-ui")
    print(f"  Stream:     http://{LLM_PROXY_HOST}:{PORT}/generate-ui-stream")
    print(f"  API Format: {LLM_API_FORMAT}")
    print(f"  LLM URL:    {LLM_BASE_URL or '(not set)'}")
    print(f"  Model:      {LLM_MODEL or '(not set)'}")
    print(f"  API Key:    {'***' + LLM_API_KEY[-4:] if len(LLM_API_KEY) > 4 else '(not set)'}")
    print(f"  Proxy Auth: {'token required' if LLM_PROXY_TOKEN else 'none'}")
    print(f"  Timeout:    {LLM_REQUEST_TIMEOUT}s")
    print()

    server = ThreadingHTTPServer((LLM_PROXY_HOST, PORT), ProxyHandler)
    print(f"Listening on {LLM_PROXY_HOST}:{PORT}...")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down.")
        server.server_close()


if __name__ == "__main__":
    main()
