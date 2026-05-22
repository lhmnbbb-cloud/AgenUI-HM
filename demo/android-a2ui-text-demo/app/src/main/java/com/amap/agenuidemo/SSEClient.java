package com.amap.agenuidemo;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SSEClient {

    private static final String TAG = "SSEClient";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 0; // 0 = no timeout for streaming

    private HttpURLConnection conn;
    private volatile boolean cancelled;
    private String accumulatedText = "";
    private int chunkCount;
    private String lastChunkPreview = "";

    public interface SSECallback {
        void onConnected();
        void onChunk(String delta, int chunkIndex, String preview);
        void onComplete(String fullText);
        void onError(Exception e, String partialText);
    }

    public void connect(String urlStr, String jsonBody, String proxyToken, SSECallback callback) {
        cancelled = false;
        accumulatedText = "";
        chunkCount = 0;
        lastChunkPreview = "";

        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setRequestProperty("Cache-Control", "no-cache");
            if (proxyToken != null && !proxyToken.isEmpty()) {
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
            if (code < 200 || code >= 300) {
                BufferedReader errReader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder errSb = new StringBuilder();
                String line;
                while ((line = errReader.readLine()) != null) {
                    errSb.append(line);
                }
                errReader.close();
                callback.onError(new RuntimeException("HTTP " + code + ": " + errSb), accumulatedText);
                return;
            }

            callback.onConnected();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));

            StringBuilder dataBuffer = new StringBuilder();
            String line;
            while (!cancelled) {
                line = reader.readLine();
                if (line == null) {
                    // Stream ended without [DONE]
                    break;
                }

                if (line.startsWith(":")) {
                    // SSE comment, skip
                    continue;
                }

                if (line.isEmpty()) {
                    // Blank line = dispatch event
                    if (dataBuffer.length() > 0) {
                        String data = dataBuffer.toString();
                        dataBuffer.setLength(0);
                        if (!dispatchData(data, callback)) {
                            return; // [DONE] received
                        }
                    }
                    continue;
                }

                if (line.startsWith("data:")) {
                    String payload = line.substring(5);
                    if (payload.startsWith(" ")) {
                        payload = payload.substring(1);
                    }
                    if (payload.isEmpty()) {
                        continue;
                    }
                    // If we already have buffered data, this is a multi-line event
                    if (dataBuffer.length() > 0) {
                        dataBuffer.append("\n");
                    }
                    dataBuffer.append(payload);
                }
                // Other SSE fields (event:, id:, retry:) are ignored
            }

            reader.close();

            if (cancelled) {
                callback.onError(new RuntimeException("Cancelled"), accumulatedText);
            } else {
                callback.onComplete(accumulatedText);
            }

        } catch (Exception e) {
            if (cancelled) {
                callback.onError(new RuntimeException("Cancelled"), accumulatedText);
            } else {
                Log.e(TAG, "SSE error", e);
                callback.onError(e, accumulatedText);
            }
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
                conn = null;
            }
        }
    }

    /**
     * Dispatch a single SSE data event.
     * @return true if streaming should continue, false if [DONE] received
     */
    private boolean dispatchData(String data, SSECallback callback) {
        if ("[DONE]".equals(data.trim())) {
            callback.onComplete(accumulatedText);
            return false;
        }

        // Check for error payload from proxy
        if (data.startsWith("{")) {
            try {
                org.json.JSONObject obj = new org.json.JSONObject(data);
                if (obj.has("error")) {
                    callback.onError(
                            new RuntimeException("Proxy error: " + obj.getString("error")),
                            accumulatedText);
                    return false;
                }
            } catch (Exception ignored) {
                // Not JSON or no error key — treat as text delta
            }
        }

        // Forward as text delta
        chunkCount++;
        accumulatedText += data;
        String preview = data.length() > 40 ? data.substring(0, 40) + "..." : data;
        lastChunkPreview = preview;
        callback.onChunk(data, chunkCount, preview);
        return true;
    }

    public void cancel() {
        cancelled = true;
        if (conn != null) {
            try { conn.disconnect(); } catch (Exception ignored) {}
        }
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public String getLastChunkPreview() {
        return lastChunkPreview;
    }

    public String getAccumulatedText() {
        return accumulatedText;
    }
}
