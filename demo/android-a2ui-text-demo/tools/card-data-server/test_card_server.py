#!/usr/bin/env python3
"""
Test script for the Mock CardData Server.

Usage:
  python test_card_server.py [port]
"""

import json
import sys
import urllib.request
import urllib.error


def test_endpoint(port, user_input, description, expect_ok=True):
    url = f"http://127.0.0.1:{port}/generate-card"
    print(f"\n=== Test: {description} ===")
    print(f"Input: {user_input}")

    request_body = {
        "userInput": user_input,
        "version": "0.1",
        "supportedCardTypes": ["sports_score_list", "weather_summary"]
    }

    try:
        req = urllib.request.Request(url, data=json.dumps(request_body).encode("utf-8"), method="POST")
        req.add_header("Content-Type", "application/json")

        with urllib.request.urlopen(req, timeout=10) as resp:
            response_body = resp.read().decode("utf-8")
            response = json.loads(response_body)
            print(f"Status: {resp.code} OK")
            print(f"CardType: {response['cardData']['cardType']}")
            debug = response.get("debug", {})
            if debug:
                print(f"Debug: intent={debug.get('matchedIntent')} provider={debug.get('provider')}")
            print(f"Response: {json.dumps(response, ensure_ascii=False, indent=2)}")
            return True if expect_ok else False

    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8", errors="replace")
        print(f"Status: {e.code} {'OK (expected)' if not expect_ok else 'ERROR'}")
        print(f"Response: {error_body}")
        return not expect_ok
    except Exception as e:
        print(f"Error: {e}")
        return False


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8766

    print("Testing Mock CardData Server")
    print(f"Endpoint: http://127.0.0.1:{port}/generate-card")

    tests = [
        ("北京天气", "Weather card by Chinese keyword", True),
        ("weather in shanghai", "Weather card by English keyword", True),
        ("NBA 今日比分", "Sports card by Chinese keyword", True),
        ("basketball scores", "Sports card by English keyword", True),
        ("something else", "Unknown intent returns 400", False),
    ]

    passed = 0
    for user_input, description, expect_ok in tests:
        if test_endpoint(port, user_input, description, expect_ok):
            passed += 1

    print(f"\n=== Summary: {passed}/{len(tests)} tests passed ===")
    sys.exit(0 if passed == len(tests) else 1)


if __name__ == "__main__":
    main()
