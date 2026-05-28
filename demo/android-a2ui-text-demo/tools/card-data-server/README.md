# Mock CardData Server

A simple mock HTTP server that returns CardData JSON for the A2UI Text Demo.

## Purpose

This server simulates what a future AI group service might return: structured CardData JSON instead of raw A2UI JSON. The Android app then renders this CardData using its local template system.

This pattern:
1. Reduces LLM output complexity (no need to generate correct A2UI component structure)
2. Ensures consistent UI rendering via local templates
3. Allows UI updates without changing model prompts
4. Validates end-to-end flow from user input to rendered UI

## Supported Card Types

- `weather_summary`: Weather information with current conditions, temperature, and tips
- `sports_score_list`: Sports game scores and statuses

## Usage

### Start the server

```bash
cd demo/android-a2ui-text-demo/tools/card-data-server
python server.py
```

### Configure environment variables (optional)

- `CARD_SERVER_HOST`: Bind address (default: 127.0.0.1)
- `CARD_SERVER_PORT`: Port number (default: 8766)

To allow access from real devices (not just emulator):

PowerShell:
```powershell
$env:CARD_SERVER_HOST="0.0.0.0"
python server.py
```

cmd:
```cmd
set CARD_SERVER_HOST=0.0.0.0
python server.py
```

### Android app configuration

In the Text Demo app:
1. Select "Card HTTP" from the Provider spinner
2. Verify the endpoint URL: `http://10.0.2.2:8766/generate-card` (for emulator)
   - For real devices: Use your computer's LAN IP instead of `10.0.2.2`
3. Enter text like:
   - "北京天气" → triggers weather_summary card
   - "NBA 比分" → triggers sports_score_list card
4. Tap "生成 UI"

### Request format

The app sends:
```json
{
  "userInput": "北京天气",
  "version": "0.1",
  "supportedCardTypes": ["sports_score_list", "weather_summary"]
}
```

### Response format

The server returns on success:
```json
{
  "cardData": {
    "requestId": "weather-123",
    "cardType": "weather_summary",
    "title": "今日天气",
    ...
  },
  "debug": {
    "provider": "mock-card-server",
    "matchedIntent": "weather_query",
    "cardType": "weather_summary"
  }
}
```

On unknown intent, the server returns HTTP 400:
```json
{
  "error": "Unknown intent for input '...'. Try: '北京天气' (weather) or 'NBA 比分' (sports).",
  "debug": {
    "provider": "mock-card-server",
    "matchedIntent": "unknown",
    "supportedIntents": ["weather_query", "sports_query"]
  }
}
```

## Test the server

A test script is included:

```bash
python test_card_server.py
```

This sends test requests and prints responses.
