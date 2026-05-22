package com.amap.agenuidemo;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.amap.agenui.render.surface.SurfaceManager;

import java.util.ArrayList;
import java.util.List;

public class StreamingSimulator {

    private static final String TAG = "StreamingSimulator";

    private final SurfaceManager surfaceManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int chunkSize = 20;
    private int delayMs = 50;
    private boolean cancelled = false;

    public interface StreamCallback {
        void onChunkSent(int chunkIndex, int totalChunks, String chunkPreview);
        void onStreamComplete();
        void onStreamError(Exception e);
    }

    public StreamingSimulator(SurfaceManager surfaceManager) {
        this.surfaceManager = surfaceManager;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = Math.max(1, chunkSize);
    }

    public void setDelayMs(int delayMs) {
        this.delayMs = Math.max(0, delayMs);
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getDelayMs() {
        return delayMs;
    }

    public void stream(String[] messages, StreamCallback callback) {
        cancelled = false;

        List<String> allChunks = new ArrayList<>();
        for (String message : messages) {
            if (message == null || message.trim().equals("{}")) continue;
            for (int i = 0; i < message.length(); i += chunkSize) {
                int end = Math.min(i + chunkSize, message.length());
                allChunks.add(message.substring(i, end));
            }
        }

        if (allChunks.isEmpty()) {
            if (callback != null) callback.onStreamComplete();
            return;
        }

        surfaceManager.beginTextStream();
        int totalChunks = allChunks.size();
        scheduleChunk(allChunks, 0, totalChunks, callback);
    }

    private void scheduleChunk(List<String> allChunks, int index, int totalChunks,
                               StreamCallback callback) {
        if (cancelled) return;

        if (index >= allChunks.size()) {
            try {
                surfaceManager.endTextStream();
            } catch (Exception e) {
                Log.e(TAG, "Error ending stream", e);
            }
            if (callback != null) callback.onStreamComplete();
            return;
        }

        String chunk = allChunks.get(index);
        try {
            surfaceManager.receiveTextChunk(chunk);
        } catch (Exception e) {
            if (callback != null) callback.onStreamError(e);
            return;
        }

        if (callback != null) {
            String preview = chunk.length() > 30 ? chunk.substring(0, 30) + "..." : chunk;
            callback.onChunkSent(index, totalChunks, preview);
        }

        handler.postDelayed(() ->
                scheduleChunk(allChunks, index + 1, totalChunks, callback), delayMs);
    }

    public void cancel() {
        cancelled = true;
        try {
            surfaceManager.endTextStream();
        } catch (Exception e) {
            Log.e(TAG, "Error ending stream on cancel", e);
        }
    }
}
