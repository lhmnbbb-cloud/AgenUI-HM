package com.amap.agenuidemo;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class LLMUiGenerationProvider implements UiGenerationProvider {

    private static final String TAG = "LLMProvider";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 120000;

    private String endpointUrl = "http://10.0.2.2:8787/generate-ui";
    private String proxyToken = "";
    private String rawResponse = "";

    public void setEndpointUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            this.endpointUrl = url.trim();
        }
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setProxyToken(String token) {
        this.proxyToken = token != null ? token : "";
    }

    public String getRawResponse() {
        return rawResponse;
    }

    @Override
    public String[] generate(String userInput) throws LLMException {
        rawResponse = "";
        try {
            JSONObject requestBody = buildRequestBody(userInput);
            String response = doPost(endpointUrl, requestBody.toString());
            rawResponse = response;
            return parseResponse(response);
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMException("LLM request failed: " + e.getMessage(), rawResponse);
        }
    }

    public static String deriveStreamUrl(String baseUrl) {
        if (baseUrl == null) return null;
        if (baseUrl.endsWith("/generate-ui")) {
            return baseUrl.substring(0, baseUrl.length() - "/generate-ui".length()) + "/generate-ui-stream";
        }
        return baseUrl + "-stream";
    }

    public static String buildRequestBodyJson(String userInput) throws Exception {
        JSONObject body = new JSONObject();
        body.put("userInput", userInput != null ? userInput : "");
        body.put("version", "v0.9");
        JSONArray catalog = new JSONArray();
        catalog.put("Text").put("Image").put("Icon").put("Video").put("AudioPlayer");
        catalog.put("Row").put("Column").put("List").put("Card").put("Tabs").put("Modal").put("Divider");
        catalog.put("Button").put("TextField").put("CheckBox").put("ChoicePicker").put("Slider").put("DateTimeInput");
        catalog.put("RichText").put("Table").put("Web").put("Carousel");
        body.put("availableComponents", catalog);
        return body.toString();
    }

    private JSONObject buildRequestBody(String userInput) throws Exception {
        return new JSONObject(buildRequestBodyJson(userInput));
    }

    private String doPost(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            if (!proxyToken.isEmpty()) {
                conn.setRequestProperty("X-Proxy-Token", proxyToken);
            }
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
                throw new LLMException("HTTP " + code + ": " + responseBody, rawResponse);
            }

            return responseBody;
        } finally {
            conn.disconnect();
        }
    }

    private String[] parseResponse(String responseBody) throws Exception {
        JSONObject json = new JSONObject(responseBody);

        // Accept both {"messages": [...]} and direct array responses
        JSONArray messagesArray;
        if (json.has("messages")) {
            messagesArray = json.getJSONArray("messages");
        } else if (json.has("a2ui")) {
            messagesArray = json.getJSONArray("a2ui");
        } else {
            throw new LLMException("Response missing 'messages' or 'a2ui' array", rawResponse);
        }

        String[] messages = new String[3];
        for (int i = 0; i < 3; i++) {
            if (i < messagesArray.length()) {
                Object item = messagesArray.get(i);
                if (item instanceof JSONObject) {
                    messages[i] = ((JSONObject) item).toString();
                } else {
                    messages[i] = item.toString();
                }
            } else {
                messages[i] = "{}";
            }
        }
        return messages;
    }

    public static class LLMException extends RuntimeException {
        private final String rawOutput;

        public LLMException(String message, String rawOutput) {
            super(message);
            this.rawOutput = rawOutput != null ? rawOutput : "";
        }

        public String getRawOutput() {
            return rawOutput;
        }
    }
}
