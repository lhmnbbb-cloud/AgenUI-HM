#!/usr/bin/env python3
"""
Mock CardData Server for A2UI Text Demo.

Receives HTTP POST from the Android app and returns mock CardData JSON
for supported card types (sports_score_list, weather_summary).

Usage:
  python server.py

  # Optional: bind to all interfaces for real-device testing (default: 127.0.0.1)
  set CARD_SERVER_HOST=0.0.0.0

  # Optional: change port (default: 8766)
  set CARD_SERVER_PORT=8766

The Android emulator accesses this via http://10.0.2.2:8766/generate-card
Real devices use http://<your-pc-ip>:8766/generate-card
"""

import json
import os
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn

CARD_SERVER_HOST = os.environ.get("CARD_SERVER_HOST", "127.0.0.1")
PORT = int(os.environ.get("CARD_SERVER_PORT", "8766"))


MOCK_WEATHER = {
    "requestId": "weather-123",
    "cardType": "weather_summary",
    "title": "今日天气",
    "location": "北京",
    "updatedAt": "14:30 更新",
    "current": {
        "condition": "晴转多云",
        "temperature": 26,
        "high": 30,
        "low": 18,
        "airQuality": "良",
        "humidity": "65%",
        "wind": "东南风 3级"
    },
    "tips": ["紫外线较强，注意防晒", "早晚温差较大，注意添衣"]
}

MOCK_SPORTS = {
    "requestId": "sports-456",
    "cardType": "sports_score_list",
    "title": "NBA 今日赛果",
    "subtitle": "2024-25 赛季常规赛",
    "updatedAt": "14:30 更新",
    "items": [
        {
            "gameId": "g001",
            "status": "final",
            "startTime": "09:00",
            "summary": "湖人终结连败",
            "homeTeam": {"name": "湖人", "score": 112},
            "awayTeam": {"name": "勇士", "score": 108}
        },
        {
            "gameId": "g002",
            "status": "live",
            "startTime": "10:30",
            "summary": "第三节进行中",
            "homeTeam": {"name": "凯尔特人", "score": 98},
            "awayTeam": {"name": "热火", "score": 102}
        },
        {
            "gameId": "g003",
            "status": "scheduled",
            "startTime": "19:00",
            "homeTeam": {"name": "掘金"},
            "awayTeam": {"name": "太阳"}
        }
    ]
}


class MockCardHandler(BaseHTTPRequestHandler):

    def _send_json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.path != "/generate-card":
            self.send_error(404, "Not found")
            return

        try:
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length).decode("utf-8")
            request = json.loads(body)
        except Exception as e:
            self._send_json(400, {"error": f"Invalid request body: {e}"})
            return

        user_input = request.get("userInput", "")
        supported_types = request.get("supportedCardTypes", [])

        if not user_input.strip():
            self._send_json(400, {"error": "userInput is required"})
            return

        # Simple keyword matching to pick card type
        user_input_lower = user_input.lower()

        matched_intent = None
        if any(k in user_input_lower for k in ["天气", "weather", "气温"]):
            card_data = MOCK_WEATHER
            matched_intent = "weather_query"
        elif any(k in user_input_lower for k in ["体育", "sports", "篮球", "nba", "比赛", "比分", "score"]):
            card_data = MOCK_SPORTS
            matched_intent = "sports_query"
        else:
            # Unknown intent — return error with supported hints
            self._send_json(400, {
                "error": f"Unknown intent for input '{user_input[:50]}'. "
                         "Try: '北京天气' (weather) or 'NBA 比分' (sports).",
                "debug": {
                    "provider": "mock-card-server",
                    "matchedIntent": "unknown",
                    "supportedIntents": ["weather_query", "sports_query"]
                }
            })
            return

        card_type = card_data["cardType"]

        # Check if card type is supported by the client
        if supported_types and card_type not in supported_types:
            self._send_json(400, {
                "error": f"Card type '{card_type}' not in client supported types: {supported_types}",
                "debug": {
                    "provider": "mock-card-server",
                    "matchedIntent": matched_intent,
                    "cardType": card_type
                }
            })
            return

        self._send_json(200, {
            "cardData": card_data,
            "debug": {
                "provider": "mock-card-server",
                "matchedIntent": matched_intent,
                "cardType": card_type
            }
        })
        print(f"[OK] userInput='{user_input[:50]}' -> intent='{matched_intent}' cardType='{card_type}'")


class ThreadingHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True


def main():
    print(f"A2UI Mock CardData Server")
    print(f"  Endpoint:  http://{CARD_SERVER_HOST}:{PORT}/generate-card")
    print()
    print("Supported card types:")
    print(f"  - weather_summary: trigger with keywords like '天气', 'weather'")
    print(f"  - sports_score_list: trigger with keywords like '体育', 'NBA', '比分'")
    print()

    server = ThreadingHTTPServer((CARD_SERVER_HOST, PORT), MockCardHandler)
    print(f"Listening on {CARD_SERVER_HOST}:{PORT}...")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down.")
        server.server_close()


if __name__ == "__main__":
    main()
