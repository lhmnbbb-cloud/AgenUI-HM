# A2UI LLM Proxy Server

Local proxy that bridges the Android demo app to a real LLM API for A2UI JSON generation.

## Quick Start

```bash
cd tools/llm-proxy

# Set your LLM API key (required)
$env:LLM_API_KEY="your-api-key"       # Windows PowerShell
export LLM_API_KEY="your-api-key"      # macOS / Linux

# Required: set API endpoint and model
$env:LLM_BASE_URL="https://your-llm-endpoint.example.com"
$env:LLM_MODEL="your-model-name"

# Optional: choose API format (default: anthropic)
$env:LLM_API_FORMAT="openai"           # or "anthropic"

# Start the server (default: 127.0.0.1:8787)
python server.py
```

For real-device testing, bind to all interfaces:
```bash
$env:LLM_PROXY_HOST="0.0.0.0"
python server.py
```

For access control, set a proxy token:
```bash
$env:LLM_PROXY_TOKEN="your-secret-token"
```
When `LLM_PROXY_TOKEN` is set, the Android app must send the same value in the `X-Proxy-Token` header.

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `LLM_API_KEY` | Yes | — | API key for the LLM provider |
| `LLM_BASE_URL` | Yes | — | Base URL of the LLM API |
| `LLM_MODEL` | Yes | — | Model name to use |
| `LLM_API_FORMAT` | No | `anthropic` | API format: `anthropic` or `openai` (other values cause startup error) |
| `LLM_PROXY_HOST` | No | `127.0.0.1` | Bind address (use `0.0.0.0` for real-device access) |
| `LLM_PROXY_PORT` | No | `8787` | Port to listen on |
| `LLM_PROXY_TOKEN` | No | — | Optional token for `X-Proxy-Token` authentication |
| `LLM_REQUEST_TIMEOUT` | No | `100` | LLM API request timeout in seconds (must not exceed Android app read timeout) |

## How It Works

### Synchronous Endpoint: POST /generate-ui

1. Android app sends POST to `/generate-ui` with body:
   ```json
   {
     "userInput": "显示天气卡片",
     "version": "v0.9",
     "availableComponents": ["Text", "Image", "Button", ...]
   }
   ```

2. Proxy constructs a prompt with A2UI v0.9 system instructions and calls the LLM.

3. LLM returns A2UI JSON array `[createSurface, updateComponents, updateDataModel]`.

4. Proxy validates and forwards to Android app:
   ```json
   {
     "messages": [
       {"version": "v0.9", "createSurface": {...}},
       {"version": "v0.9", "updateComponents": {...}},
       {}
     ]
   }
   ```

5. Android app validates via `A2uiJsonValidator` before rendering.

### Streaming Endpoint: POST /generate-ui-stream

When the Android app has LLM provider + Stream ON, it uses the SSE endpoint instead:

1. Android app sends POST to `/generate-ui-stream` with the same request body as `/generate-ui`.

2. Proxy opens a streaming connection to the LLM API (`"stream": true`).

3. Proxy forwards each text delta as an SSE event:
   ```
   data: {"version":"v0.9","createSurface":...
   data: {"surfaceId":"...
   data: [DONE]
   ```

4. Android app calls `surfaceManager.beginTextStream()` on connect, `receiveTextChunk(delta)` for each event, and `endTextStream()` when `[DONE]` is received.

5. After stream ends, the accumulated text is validated via `A2uiJsonValidator.validateFromRawText()`.

SSE format:
- `Content-Type: text/event-stream`
- Each data line: `data: <text delta>\n\n`
- Terminal event: `data: [DONE]\n\n`
- On error mid-stream: `data: {"error":"..."}\n\n` then `data: [DONE]\n\n`

URL derivation: `/generate-ui` → `/generate-ui-stream` (replace suffix).

## Concurrency

The server uses `ThreadingHTTPServer` so multiple requests are handled concurrently (one thread per request).

## Startup Validation

If `LLM_API_FORMAT` is set to anything other than `anthropic` or `openai`, the server prints an error and exits immediately.

## Compatible LLM Providers

Any OpenAI-compatible or Anthropic-compatible API works. Set `LLM_BASE_URL` and `LLM_API_FORMAT` accordingly:

| Provider | LLM_BASE_URL | LLM_API_FORMAT |
|---|---|---|
| OpenAI | `https://api.openai.com` | `openai` |
| Azure OpenAI | `https://<resource>.openai.azure.com` | `openai` |
| 通义千问 (DashScope) | `https://dashscope.aliyuncs.com/compatible-mode` | `openai` |
| DeepSeek | `https://api.deepseek.com` | `openai` |
| Anthropic | `https://api.anthropic.com` | `anthropic` |
| Anthropic-compatible | your endpoint URL | `anthropic` |

## Android App Access

- Emulator: `http://10.0.2.2:8787/generate-ui`
- Real device: `http://<your-pc-ip>:8787/generate-ui` (requires `LLM_PROXY_HOST=0.0.0.0`)

## Requirements

- Python 3.7+ (no extra packages needed — uses only stdlib)
- An API key for your chosen LLM provider
- All three required env vars must be set: `LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`
