package com.amap.agenuidemo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class CardHttpProvider {

    private static final int CONNECT_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 30000;

    private String endpointUrl = "http://10.0.2.2:8766/generate-card";
    private String rawResponse = "";

    public void setEndpointUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            this.endpointUrl = url.trim();
        }
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public String fetchCardData(String userInput) throws CardHttpException {
        rawResponse = "";
        try {
            JSONObject requestBody = buildRequestBody(userInput);
            String response = doPost(endpointUrl, requestBody.toString());
            rawResponse = response;
            return parseCardData(response);
        } catch (CardHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new CardHttpException("Card HTTP request failed: " + e.getMessage(), rawResponse);
        }
    }

    static JSONObject buildRequestBodyObject(String userInput) throws Exception {
        JSONObject body = new JSONObject();
        body.put("userInput", userInput != null ? userInput : "");
        body.put("version", "0.1");
        JSONArray supportedCardTypes = new JSONArray();
        supportedCardTypes.put("sports_score_list");
        supportedCardTypes.put("weather_summary");
        body.put("supportedCardTypes", supportedCardTypes);
        return body;
    }

    public static String buildRequestBodyJson(String userInput) throws Exception {
        return buildRequestBodyObject(userInput).toString();
    }

    private JSONObject buildRequestBody(String userInput) throws Exception {
        return buildRequestBodyObject(userInput);
    }

    private String doPost(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);

            byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(bodyBytes);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            BufferedReader reader;
            if (code >= 200 && code < 300) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            } else {
                reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
            }

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            String responseBody = sb.toString();
            if (code < 200 || code >= 300) {
                rawResponse = responseBody;
                String errorMsg = responseBody;
                try {
                    JSONObject errorObj = new JSONObject(responseBody);
                    if (errorObj.has("error")) {
                        errorMsg = errorObj.getString("error");
                    }
                } catch (Exception ignored) {
                }
                throw new CardHttpException("HTTP " + code + ": " + errorMsg, rawResponse);
            }

            return responseBody;
        } finally {
            conn.disconnect();
        }
    }

    static String parseCardData(String responseBody) throws Exception {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            throw new CardHttpException("Empty response from server", responseBody);
        }

        // First, make sure response is valid JSON
        JSONObject json;
        try {
            json = new JSONObject(responseBody);
        } catch (Exception e) {
            throw new CardHttpException("Response is not valid JSON", responseBody);
        }

        // Case 1: Response has "cardData" wrapper
        if (json.has("cardData")) {
            Object cardDataObj = json.get("cardData");
            if (cardDataObj instanceof JSONObject) {
                return ((JSONObject) cardDataObj).toString();
            } else if (cardDataObj instanceof String) {
                String cardDataStr = (String) cardDataObj;
                try {
                    new JSONObject(cardDataStr); // Verify it's valid JSON
                    return cardDataStr;
                } catch (Exception e) {
                    throw new CardHttpException("cardData string is not valid JSON", responseBody);
                }
            }
            throw new CardHttpException("cardData is not an object or string", responseBody);
        }

        // Case 2: Response is already cardData directly (has cardType or requestId)
        if (json.has("cardType") || json.has("requestId")) {
            return responseBody;
        }

        throw new CardHttpException("Could not parse cardData from response", responseBody);
    }

    public static class CardHttpException extends RuntimeException {
        private final String rawOutput;

        public CardHttpException(String message, String rawOutput) {
            super(message);
            this.rawOutput = rawOutput != null ? rawOutput : "";
        }

        public String getRawOutput() {
            return rawOutput;
        }
    }
}
