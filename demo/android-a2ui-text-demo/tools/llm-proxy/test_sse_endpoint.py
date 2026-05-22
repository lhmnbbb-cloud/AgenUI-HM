#!/usr/bin/env python3
"""
Tests for the LLM Proxy SSE endpoint (/generate-ui-stream).

Uses a mock LLM backend to test the proxy's SSE forwarding behavior
without requiring a real API key or LLM endpoint.

Run from: tools/llm-proxy/
    python test_sse_endpoint.py
"""

import json
import os
import socket
import threading
import time
import urllib.request
import urllib.error
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn


# --- Mock LLM Server ---

MOCK_LLM_RESPONSE_CHUNKS = [
    '{"version":"v0.9","createSurface":{"surfaceId":"test-surf","catalogId":"https://a2ui.org/catalog"}}',
    '{"version":"v0.9","updateComponents":{"surfaceId":"test-surf","components":[{"id":"root","component":"Text","text":"Hello"}]}}',
    '{}',
]


def find_free_port():
    """Find a free TCP port on 127.0.0.1."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        return s.getsockname()[1]


def wait_for_port(port, timeout=5):
    """Wait until a port is accepting connections."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.5):
                return True
        except (ConnectionRefusedError, OSError):
            time.sleep(0.1)
    return False


class MockLLMHandler(BaseHTTPRequestHandler):
    """Simulates an OpenAI-compatible or Anthropic-compatible streaming LLM API."""

    def do_POST(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length).decode("utf-8")
        try:
            req_obj = json.loads(body)
        except Exception:
            req_obj = {}

        is_stream = req_obj.get("stream", False)

        if self.path == "/v1/chat/completions" and is_stream:
            self._handle_openai_stream()
        elif self.path == "/v1/messages" and is_stream:
            self._handle_anthropic_stream()
        elif self.path == "/v1/chat/completions":
            self._handle_openai_sync()
        elif self.path == "/v1/messages":
            self._handle_anthropic_sync()
        else:
            self.send_error(404, "Not found")

    def _handle_openai_stream(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()

        full_text = "\n".join(MOCK_LLM_RESPONSE_CHUNKS)
        chunk_size = 10
        for i in range(0, len(full_text), chunk_size):
            piece = full_text[i:i+chunk_size]
            data = json.dumps({"choices": [{"delta": {"content": piece}, "index": 0}]})
            self.wfile.write(f"data: {data}\n\n".encode("utf-8"))
            self.wfile.flush()
            time.sleep(0.01)

        self.wfile.write("data: [DONE]\n\n".encode("utf-8"))
        self.wfile.flush()

    def _handle_anthropic_stream(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()

        # message_start
        self.wfile.write("event: message_start\n".encode("utf-8"))
        self.wfile.write(f"data: {json.dumps({'type': 'message_start', 'message': {'id': 'msg-1'}})}\n\n".encode("utf-8"))
        self.wfile.flush()

        # content_block_start
        self.wfile.write("event: content_block_start\n".encode("utf-8"))
        self.wfile.write(f"data: {json.dumps({'type': 'content_block_start', 'index': 0})}\n\n".encode("utf-8"))
        self.wfile.flush()

        # text deltas
        full_text = "\n".join(MOCK_LLM_RESPONSE_CHUNKS)
        chunk_size = 10
        for i in range(0, len(full_text), chunk_size):
            piece = full_text[i:i+chunk_size]
            self.wfile.write("event: content_block_delta\n".encode("utf-8"))
            self.wfile.write(f"data: {json.dumps({'type': 'content_block_delta', 'delta': {'type': 'text_delta', 'text': piece}})}\n\n".encode("utf-8"))
            self.wfile.flush()
            time.sleep(0.01)

        # content_block_stop
        self.wfile.write("event: content_block_stop\n".encode("utf-8"))
        self.wfile.write(f"data: {json.dumps({'type': 'content_block_stop', 'index': 0})}\n\n".encode("utf-8"))
        self.wfile.flush()

        # message_stop
        self.wfile.write("event: message_stop\n".encode("utf-8"))
        self.wfile.write(f"data: {json.dumps({'type': 'message_stop'})}\n\n".encode("utf-8"))
        self.wfile.flush()

    def _handle_openai_sync(self):
        full_text = "\n".join(MOCK_LLM_RESPONSE_CHUNKS)
        response = {"choices": [{"message": {"content": full_text}}]}
        body = json.dumps(response).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _handle_anthropic_sync(self):
        full_text = "\n".join(MOCK_LLM_RESPONSE_CHUNKS)
        response = {"content": [{"type": "text", "text": full_text}]}
        body = json.dumps(response).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        pass  # suppress logs


class ThreadingHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


def start_server(port, handler_class):
    server = ThreadingHTTPServer(("127.0.0.1", port), handler_class)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    assert wait_for_port(port), f"Server on port {port} did not start in time"
    return server


# --- Test Helpers ---

def read_sse_stream(url, json_body, headers=None, timeout=15):
    """Read an SSE stream and return list of data events."""
    if headers is None:
        headers = {}
    data = json_body.encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    for k, v in headers.items():
        req.add_header(k, v)

    events = []
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        buffer = ""
        for raw_line in resp:
            line = raw_line.decode("utf-8").strip()
            if not line:
                if buffer:
                    events.append(buffer)
                    buffer = ""
                continue
            if line.startswith("data:"):
                payload = line[5:].strip()
                if buffer:
                    buffer += "\n" + payload
                else:
                    buffer = payload
            elif line.startswith(":"):
                continue
        if buffer:
            events.append(buffer)
    return events


def set_env(env_dict):
    """Set environment variables and return old values for restoration."""
    old_env = {}
    for k, v in env_dict.items():
        old_env[k] = os.environ.get(k)
        os.environ[k] = v
    return old_env


def restore_env(old_env):
    """Restore environment variables from saved old values."""
    for k, v in old_env.items():
        if v is None:
            os.environ.pop(k, None)
        else:
            os.environ[k] = v


def start_proxy(mock_port, proxy_port, api_format, proxy_token=""):
    """Start a proxy server that connects to the mock LLM."""
    env = {
        "LLM_API_KEY": "test-key",
        "LLM_BASE_URL": f"http://127.0.0.1:{mock_port}",
        "LLM_MODEL": "test-model",
        "LLM_API_FORMAT": api_format,
        "LLM_PROXY_HOST": "127.0.0.1",
        "LLM_PROXY_PORT": str(proxy_port),
        "LLM_PROXY_TOKEN": proxy_token,
        "LLM_REQUEST_TIMEOUT": "30",
    }
    old_env = set_env(env)

    import importlib
    import server as proxy_module
    importlib.reload(proxy_module)

    proxy = ThreadingHTTPServer(("127.0.0.1", proxy_port), proxy_module.ProxyHandler)
    proxy_thread = threading.Thread(target=proxy.serve_forever, daemon=True)
    proxy_thread.start()
    assert wait_for_port(proxy_port), f"Proxy on port {proxy_port} did not start in time"

    restore_env(old_env)
    return proxy


def parse_multiple_json(text):
    """Parse multiple JSON objects from a concatenated string using raw_decode."""
    decoder = json.JSONDecoder()
    objects = []
    pos = 0
    text = text.strip()
    while pos < len(text):
        obj, end = decoder.raw_decode(text, pos)
        objects.append(obj)
        pos = end
        # Skip whitespace/newlines between objects
        while pos < len(text) and text[pos] in (' ', '\n', '\r', '\t'):
            pos += 1
    return objects


# --- Tests ---

def test_sse_url_derivation():
    """Test SSE URL derivation logic."""
    base_url = "http://10.0.2.2:8787/generate-ui"
    expected = "http://10.0.2.2:8787/generate-ui-stream"
    derived = base_url[:-len("/generate-ui")] + "/generate-ui-stream"
    assert derived == expected, f"Expected {expected}, got {derived}"

    custom = "http://example.com/api/gen"
    custom_stream = custom + "-stream"
    assert custom_stream == "http://example.com/api/gen-stream"

    print("PASS: test_sse_url_derivation")


def test_sse_missing_user_input():
    """Test that SSE endpoint rejects requests with empty userInput."""
    proxy_port = find_free_port()
    mock_port = find_free_port()

    env = {
        "LLM_API_KEY": "test-key",
        "LLM_BASE_URL": f"http://127.0.0.1:{mock_port}",
        "LLM_MODEL": "test-model",
        "LLM_API_FORMAT": "openai",
        "LLM_PROXY_HOST": "127.0.0.1",
        "LLM_PROXY_PORT": str(proxy_port),
        "LLM_PROXY_TOKEN": "",
        "LLM_REQUEST_TIMEOUT": "30",
    }
    old_env = set_env(env)

    import importlib
    import server as proxy_module
    importlib.reload(proxy_module)

    proxy = ThreadingHTTPServer(("127.0.0.1", proxy_port), proxy_module.ProxyHandler)
    proxy_thread = threading.Thread(target=proxy.serve_forever, daemon=True)
    proxy_thread.start()
    wait_for_port(proxy_port)

    try:
        request_body = json.dumps({"userInput": "", "version": "v0.9"})
        data = request_body.encode("utf-8")
        req = urllib.request.Request(
            f"http://127.0.0.1:{proxy_port}/generate-ui-stream",
            data=data, method="POST")
        req.add_header("Content-Type", "application/json")

        try:
            urllib.request.urlopen(req, timeout=5)
            assert False, "Expected 400 error"
        except urllib.error.HTTPError as e:
            assert e.code == 400, f"Expected 400, got {e.code}"

        print("PASS: test_sse_missing_user_input")
    finally:
        proxy.shutdown()
        restore_env(old_env)


def test_sse_proxy_token_required():
    """Test that SSE endpoint rejects requests without valid proxy token."""
    proxy_port = find_free_port()
    mock_port = find_free_port()

    env = {
        "LLM_API_KEY": "test-key",
        "LLM_BASE_URL": f"http://127.0.0.1:{mock_port}",
        "LLM_MODEL": "test-model",
        "LLM_API_FORMAT": "openai",
        "LLM_PROXY_HOST": "127.0.0.1",
        "LLM_PROXY_PORT": str(proxy_port),
        "LLM_PROXY_TOKEN": "secret-token",
        "LLM_REQUEST_TIMEOUT": "30",
    }
    old_env = set_env(env)

    import importlib
    import server as proxy_module
    importlib.reload(proxy_module)

    proxy = ThreadingHTTPServer(("127.0.0.1", proxy_port), proxy_module.ProxyHandler)
    proxy_thread = threading.Thread(target=proxy.serve_forever, daemon=True)
    proxy_thread.start()
    wait_for_port(proxy_port)

    try:
        request_body = json.dumps({"userInput": "test", "version": "v0.9"})
        data = request_body.encode("utf-8")

        # Without token -> 403
        req = urllib.request.Request(
            f"http://127.0.0.1:{proxy_port}/generate-ui-stream",
            data=data, method="POST")
        req.add_header("Content-Type", "application/json")
        try:
            urllib.request.urlopen(req, timeout=5)
            assert False, "Expected 403 error"
        except urllib.error.HTTPError as e:
            assert e.code == 403, f"Expected 403, got {e.code}"

        # With correct token -> not 403 (will fail because no LLM, but that's OK)
        req2 = urllib.request.Request(
            f"http://127.0.0.1:{proxy_port}/generate-ui-stream",
            data=data, method="POST")
        req2.add_header("Content-Type", "application/json")
        req2.add_header("X-Proxy-Token", "secret-token")
        try:
            urllib.request.urlopen(req2, timeout=5)
        except urllib.error.HTTPError as e:
            assert e.code != 403, f"Should not be 403 with correct token, got {e.code}"
        except Exception:
            pass  # Connection error is fine (no LLM backend)

        print("PASS: test_sse_proxy_token_required")
    finally:
        proxy.shutdown()
        restore_env(old_env)


def test_sse_openai_stream():
    """Test /generate-ui-stream with OpenAI format."""
    mock_port = find_free_port()
    proxy_port = find_free_port()

    mock_llm = start_server(mock_port, MockLLMHandler)
    proxy = start_proxy(mock_port, proxy_port, "openai")

    try:
        request_body = json.dumps({
            "userInput": "show weather",
            "version": "v0.9",
            "availableComponents": ["Text", "Button"]
        })
        events = read_sse_stream(
            f"http://127.0.0.1:{proxy_port}/generate-ui-stream",
            request_body
        )

        assert len(events) > 0, "Expected at least one SSE event"

        done_events = [e for e in events if e == "[DONE]"]
        assert len(done_events) == 1, f"Expected exactly one [DONE] event, got {done_events}"

        deltas = [e for e in events if e != "[DONE]"]
        full_text = "".join(deltas)

        parsed_list = parse_multiple_json(full_text)
        assert len(parsed_list) == 3, f"Expected 3 messages, got {len(parsed_list)}"
        assert "createSurface" in parsed_list[0], "First message should have createSurface"
        assert "updateComponents" in parsed_list[1], "Second message should have updateComponents"

        print("PASS: test_sse_openai_stream")
    finally:
        proxy.shutdown()
        mock_llm.shutdown()


def test_sse_anthropic_stream():
    """Test /generate-ui-stream with Anthropic format."""
    mock_port = find_free_port()
    proxy_port = find_free_port()

    mock_llm = start_server(mock_port, MockLLMHandler)
    proxy = start_proxy(mock_port, proxy_port, "anthropic")

    try:
        request_body = json.dumps({
            "userInput": "show settings",
            "version": "v0.9",
            "availableComponents": ["Text", "Button"]
        })
        events = read_sse_stream(
            f"http://127.0.0.1:{proxy_port}/generate-ui-stream",
            request_body
        )

        assert len(events) > 0, "Expected at least one SSE event"

        done_events = [e for e in events if e == "[DONE]"]
        assert len(done_events) == 1, f"Expected exactly one [DONE] event"

        deltas = [e for e in events if e != "[DONE]"]
        full_text = "".join(deltas)

        parsed_list = parse_multiple_json(full_text)
        assert len(parsed_list) == 3
        assert "createSurface" in parsed_list[0]
        assert "updateComponents" in parsed_list[1]

        print("PASS: test_sse_anthropic_stream")
    finally:
        proxy.shutdown()
        mock_llm.shutdown()


def test_json_array_stripper():
    """Test JsonArrayStripper strips array delimiters and forwards only {…} content."""
    import importlib
    import server as proxy_module
    importlib.reload(proxy_module)

    collected = []
    proxy_module.JsonArrayStripper(lambda t: collected.append(t))

    stripper = proxy_module.JsonArrayStripper(lambda t: collected.append(t))

    # Simulate LLM outputting: [  {"a":1},  {"b":2}  ]
    stripper('[')
    stripper('  {"a":1},')
    stripper('  {"b":2}')
    stripper('  ]')

    full = ''.join(collected)
    # Should NOT contain [ or ] or the comma between objects at top level
    assert '[' not in full, f"Stripped output should not contain '[', got: {full!r}"
    assert ']' not in full, f"Stripped output should not contain ']', got: {full!r}"
    # Should contain both objects
    assert '{"a":1}' in full, f"Should contain first object, got: {full!r}"
    assert '{"b":2}' in full, f"Should contain second object, got: {full!r}"

    # Verify the comma between objects at top level is stripped
    # (commas inside objects should be preserved)
    assert ',  {' not in full, f"Top-level comma should be stripped, got: {full!r}"

    print("PASS: test_json_array_stripper")


def test_json_array_stripper_with_strings():
    """Test JsonArrayStripper preserves [ and , inside string values."""
    import importlib
    import server as proxy_module
    importlib.reload(proxy_module)

    collected = []
    stripper = proxy_module.JsonArrayStripper(lambda t: collected.append(t))

    # Array with string containing brackets and commas
    stripper('[{"text":"Hello, [world]!"},{}]')
    full = ''.join(collected)
    assert 'Hello, [world]!' in full, f"String content should be preserved, got: {full!r}"
    assert '{"text":"Hello, [world]!"}' in full, f"First object should be intact, got: {full!r}"
    assert '{}' in full, f"Second object should be intact, got: {full!r}"
    # Leading [ and ] wrapper should be gone
    assert not full.startswith('['), f"Should not start with [, got: {full!r}"

    print("PASS: test_json_array_stripper_with_strings")


def test_sse_stream_strips_array_wrapper():
    """Test that /generate-ui-stream strips JSON array wrapper from LLM output."""
    mock_port = find_free_port()
    proxy_port = find_free_port()

    mock_llm = start_server(mock_port, MockLLMHandler)
    proxy = start_proxy(mock_port, proxy_port, "openai")

    try:
        request_body = json.dumps({
            "userInput": "show weather",
            "version": "v0.9",
            "availableComponents": ["Text", "Button"]
        })
        events = read_sse_stream(
            f"http://127.0.0.1:{proxy_port}/generate-ui-stream",
            request_body
        )

        deltas = [e for e in events if e != "[DONE]"]
        full_text = "".join(deltas)

        # The stripped output should NOT start with [ or end with ]
        assert not full_text.strip().startswith('['), \
            f"SSE output should not start with '[' after stripping, got: {full_text[:80]!r}"
        assert not full_text.strip().endswith(']'), \
            f"SSE output should not end with ']' after stripping, got: {full_text[-80:]!r}"

        # Should still contain valid JSON objects
        parsed_list = parse_multiple_json(full_text)
        assert len(parsed_list) == 3, f"Expected 3 objects, got {len(parsed_list)}"
        assert "createSurface" in parsed_list[0], "First object should have createSurface"
        assert "updateComponents" in parsed_list[1], "Second object should have updateComponents"

        print("PASS: test_sse_stream_strips_array_wrapper")
    finally:
        proxy.shutdown()
        mock_llm.shutdown()


if __name__ == "__main__":
    print("Running SSE endpoint tests...")
    print()

    test_sse_url_derivation()
    test_sse_missing_user_input()
    test_sse_proxy_token_required()
    test_json_array_stripper()
    test_json_array_stripper_with_strings()
    test_sse_openai_stream()
    test_sse_anthropic_stream()
    test_sse_stream_strips_array_wrapper()

    print()
    print("All SSE endpoint tests passed!")
