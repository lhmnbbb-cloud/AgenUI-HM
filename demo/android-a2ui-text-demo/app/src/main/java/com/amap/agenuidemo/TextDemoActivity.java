package com.amap.agenuidemo;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.amap.agenui.AGenUI;
import com.amap.agenui.render.surface.ISurfaceManagerListener;
import com.amap.agenui.render.surface.Surface;
import com.amap.agenui.render.surface.SurfaceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TextDemoActivity extends AppCompatActivity {

    private static final String TAG = "A2UITextDemo";

    // UI - existing
    private EditText etInput;
    private Button btnGenerate;
    private FrameLayout renderArea;
    private TextView tvSdkVersion;
    private LinearLayout logsHeader;
    private TextView tvLogsToggle;
    private ScrollView logsScrollView;
    private LinearLayout logsContent;
    private boolean logsExpanded = false;

    // UI - controls
    private Spinner spinnerProvider;
    private Spinner spinnerFixture;
    private SwitchCompat switchStreaming;
    private EditText etLlmUrl;
    private EditText etProxyToken;

    // UI - debug
    private LinearLayout debugHeader;
    private TextView tvDebugToggle;
    private ScrollView debugScrollView;
    private TextView tvDebugProvider;
    private TextView tvDebugInput;
    private TextView tvDebugRenderStatus;
    private TextView tvDebugRawJson;
    private TextView tvDebugErrors;
    private TextView tvValidationBadge;
    private TextView tvDebugSseStatus;
    private TextView tvDebugSseLastChunk;
    private TextView tvDebugSseAccumulated;
    private boolean debugExpanded = true;

    // AGenUI
    private AGenUI aGenUI;
    private SurfaceManager surfaceManager;

    // Provider
    private ProviderType currentProviderType = ProviderType.MOCK;
    private UiGenerationProvider provider;
    private FixtureUiGenerationProvider fixtureProvider;
    private LLMUiGenerationProvider llmProvider;

    // Streaming
    private boolean streamingMode = false;
    private StreamingSimulator streamingSimulator;
    private SSEClient sseClient;
    private boolean isBusy = false;

    // SSE debug state
    private String sseStatus = "";
    private int sseChunkCount = 0;
    private String sseLastChunkPreview = "";
    private String sseAccumulatedText = "";

    // Debug state
    private String lastUserInput = "";
    private String[] lastGeneratedMessages = null;
    private String renderStatus = "idle";
    private String lastError = "";
    private String lastRawLlmOutput = "";

    // LLM async
    private final ExecutorService llmExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_demo);

        initViews();
        initAGenUI();
        initProvider();
        initControls();

        btnGenerate.setOnClickListener(v -> onGenerateClick());
        logsHeader.setOnClickListener(v -> toggleLogs());
        debugHeader.setOnClickListener(v -> toggleDebug());
    }

    private void initViews() {
        etInput = findViewById(R.id.etInput);
        btnGenerate = findViewById(R.id.btnGenerate);
        renderArea = findViewById(R.id.renderArea);
        tvSdkVersion = findViewById(R.id.tvSdkVersion);
        logsHeader = findViewById(R.id.logsHeader);
        tvLogsToggle = findViewById(R.id.tvLogsToggle);
        logsScrollView = findViewById(R.id.logsScrollView);
        logsContent = findViewById(R.id.logsContent);

        spinnerProvider = findViewById(R.id.spinnerProvider);
        spinnerFixture = findViewById(R.id.spinnerFixture);
        switchStreaming = findViewById(R.id.switchStreaming);
        etLlmUrl = findViewById(R.id.etLlmUrl);
        etProxyToken = findViewById(R.id.etProxyToken);

        debugHeader = findViewById(R.id.debugHeader);
        tvDebugToggle = findViewById(R.id.tvDebugToggle);
        debugScrollView = findViewById(R.id.debugScrollView);
        tvDebugProvider = findViewById(R.id.tvDebugProvider);
        tvDebugInput = findViewById(R.id.tvDebugInput);
        tvDebugRenderStatus = findViewById(R.id.tvDebugRenderStatus);
        tvDebugRawJson = findViewById(R.id.tvDebugRawJson);
        tvDebugErrors = findViewById(R.id.tvDebugErrors);
        tvValidationBadge = findViewById(R.id.tvValidationBadge);

        tvDebugSseStatus = findViewById(R.id.tvDebugSseStatus);
        tvDebugSseLastChunk = findViewById(R.id.tvDebugSseLastChunk);
        tvDebugSseAccumulated = findViewById(R.id.tvDebugSseAccumulated);

        tvSdkVersion.setText("AGenUI " + AGenUI.getVersion());
    }

    private void initAGenUI() {
        try {
            aGenUI = AGenUI.getInstance();
            aGenUI.initialize(getApplicationContext());
            addLog("AGenUI initialized");

            aGenUI.registerFunction(new ToastFunction(this));
            addLog("ToastFunction registered");

            surfaceManager = new SurfaceManager(this);
            addLog("SurfaceManager created");

            surfaceManager.addListener(new ISurfaceManagerListener() {
                @Override
                public void onCreateSurface(Surface surface) {
                    runOnUiThread(() -> {
                        String surfaceId = surface.getSurfaceId();
                        addLog("Surface created: " + surfaceId);

                        renderArea.removeAllViews();
                        renderArea.addView(surface.getContainer());
                        addLog("Surface container added to render area");

                        renderStatus = "rendered";
                        updateDebugInfo();
                    });
                }

                @Override
                public void onDeleteSurface(Surface surface) {
                    runOnUiThread(() -> {
                        addLog("Surface deleted: " + surface.getSurfaceId());
                        renderStatus = "idle";
                        updateDebugInfo();
                    });
                }
            });

            streamingSimulator = new StreamingSimulator(surfaceManager);
            streamingSimulator.setChunkSize(20);
            streamingSimulator.setDelayMs(50);

            addLog("AGenUI framework ready");
        } catch (Exception e) {
            addLog("AGenUI init failed: " + e.getMessage());
            Log.e(TAG, "Failed to initialize AGenUI", e);
        }
    }

    private void initProvider() {
        provider = new MockUiGenerationProvider();
        fixtureProvider = new FixtureUiGenerationProvider(this);
        llmProvider = new LLMUiGenerationProvider();
    }

    private void initControls() {
        // Provider spinner
        ArrayAdapter<ProviderType> providerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ProviderType.values());
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProvider.setAdapter(providerAdapter);
        spinnerProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ProviderType type = ProviderType.values()[position];
                switchProvider(type);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Streaming switch
        switchStreaming.setOnCheckedChangeListener((buttonView, isChecked) -> {
            streamingMode = isChecked;
            updateDebugInfo();
        });

        updateDebugInfo();
    }

    private void switchProvider(ProviderType type) {
        cancelActiveStream();
        currentProviderType = type;
        switch (type) {
            case MOCK:
                provider = new MockUiGenerationProvider();
                spinnerFixture.setVisibility(View.GONE);
                etLlmUrl.setVisibility(View.GONE);
                etProxyToken.setVisibility(View.GONE);
                break;
            case FIXTURE:
                provider = fixtureProvider;
                spinnerFixture.setVisibility(View.VISIBLE);
                etLlmUrl.setVisibility(View.GONE);
                etProxyToken.setVisibility(View.GONE);
                populateFixtureSpinner();
                break;
            case LLM:
                provider = llmProvider;
                spinnerFixture.setVisibility(View.GONE);
                etLlmUrl.setVisibility(View.VISIBLE);
                etProxyToken.setVisibility(View.VISIBLE);
                break;
        }
        updateDebugInfo();
        addLog("Provider switched to: " + type.getDisplayName());
    }

    private void populateFixtureSpinner() {
        java.util.List<String> fixtures = fixtureProvider.getAvailableFixtures();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, fixtures);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFixture.setAdapter(adapter);
        spinnerFixture.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String name = fixtures.get(position);
                fixtureProvider.selectFixture(name);
                updateDebugInfo();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void onGenerateClick() {
        if (isBusy) return;

        String input = etInput.getText().toString().trim();
        if (input.isEmpty() && currentProviderType == ProviderType.MOCK) {
            Toast.makeText(this, "请输入文本", Toast.LENGTH_SHORT).show();
            return;
        }

        if (aGenUI == null || surfaceManager == null) {
            Toast.makeText(this, "AGenUI 未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentProviderType == ProviderType.LLM) {
            handleLlmGenerate(input);
        } else {
            handleSyncGenerate(input);
        }
    }

    private void handleSyncGenerate(String input) {
        try {
            setBusy(true);
            lastUserInput = input;
            lastRawLlmOutput = "";
            renderStatus = "idle";
            lastError = "";
            lastGeneratedMessages = null;
            updateDebugInfo();

            // Generate
            String[] messages = provider.generate(input);
            lastGeneratedMessages = messages;

            // Validate
            A2uiJsonValidator.ValidationResult validation =
                    A2uiJsonValidator.validate(messages[0], messages[1], messages[2]);

            if (!validation.isValid()) {
                renderStatus = "error";
                lastError = validation.getFormattedReport();
                tvValidationBadge.setText("FAIL");
                tvValidationBadge.setTextColor(0xFFCC0000);
                setBusy(false);
                updateDebugInfo();
                addLog("Validation failed: " + validation.getErrors().size() + " error(s)");
                Toast.makeText(this, "A2UI JSON 验证失败", Toast.LENGTH_SHORT).show();
                return;
            }

            tvValidationBadge.setText("PASS");
            tvValidationBadge.setTextColor(0xFF008800);

            if (!validation.getWarnings().isEmpty()) {
                addLog("Validation warnings: " + validation.getWarnings().size());
            }

            // Stream to SDK
            renderStatus = "streaming";
            updateDebugInfo();

            if (streamingMode) {
                streamToSdk(messages);
            } else {
                sendToSdk(messages);
            }

        } catch (Exception e) {
            renderStatus = "error";
            lastError = Log.getStackTraceString(e);
            tvValidationBadge.setText("ERROR");
            tvValidationBadge.setTextColor(0xFFCC0000);
            setBusy(false);
            updateDebugInfo();
            addLog("Render failed: " + e.getMessage());
            Toast.makeText(this, "渲染失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleLlmGenerate(String input) {
        String url = etLlmUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "请输入 LLM endpoint URL", Toast.LENGTH_SHORT).show();
            return;
        }
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入文本", Toast.LENGTH_SHORT).show();
            return;
        }

        if (streamingMode) {
            handleLlmSseGenerate(input, url);
            return;
        }

        llmProvider.setEndpointUrl(url);
        llmProvider.setProxyToken(etProxyToken.getText().toString().trim());

        setBusy(true);
        lastUserInput = input;
        lastRawLlmOutput = "";
        lastError = "";
        lastGeneratedMessages = null;
        renderStatus = "generating";
        btnGenerate.setText("生成中...");
        tvValidationBadge.setText("WAIT");
        tvValidationBadge.setTextColor(0xFF996600);
        updateDebugInfo();
        addLog("LLM request to: " + url);

        llmExecutor.execute(() -> {
            try {
                String[] messages = llmProvider.generate(input);

                mainHandler.post(() -> {
                    lastGeneratedMessages = messages;
                    lastRawLlmOutput = llmProvider.getRawResponse();
                    addLog("LLM response received (" + lastRawLlmOutput.length() + " chars)");

                    // Validate
                    A2uiJsonValidator.ValidationResult validation =
                            A2uiJsonValidator.validate(messages[0], messages[1], messages[2]);

                    if (!validation.isValid()) {
                        renderStatus = "error";
                        lastError = validation.getFormattedReport();
                        tvValidationBadge.setText("FAIL");
                        tvValidationBadge.setTextColor(0xFFCC0000);
                        setBusy(false);
                        updateDebugInfo();
                        addLog("LLM validation failed: " + validation.getErrors().size() + " error(s)");
                        Toast.makeText(this, "LLM 输出验证失败，请查看 Debug 面板", Toast.LENGTH_LONG).show();
                        return;
                    }

                    tvValidationBadge.setText("PASS");
                    tvValidationBadge.setTextColor(0xFF008800);

                    if (!validation.getWarnings().isEmpty()) {
                        addLog("Validation warnings: " + validation.getWarnings().size());
                    }

                    // Stream to SDK — button stays disabled until send/stream completes
                    renderStatus = "streaming";
                    updateDebugInfo();

                    if (streamingMode) {
                        streamToSdk(messages);
                    } else {
                        sendToSdk(messages);
                    }
                });
            } catch (LLMUiGenerationProvider.LLMException e) {
                mainHandler.post(() -> {
                    renderStatus = "error";
                    lastRawLlmOutput = e.getRawOutput();
                    lastError = e.getMessage();
                    tvValidationBadge.setText("ERROR");
                    tvValidationBadge.setTextColor(0xFFCC0000);
                    setBusy(false);
                    updateDebugInfo();
                    addLog("LLM error: " + e.getMessage());
                    Toast.makeText(this, "LLM 请求失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    renderStatus = "error";
                    lastError = Log.getStackTraceString(e);
                    tvValidationBadge.setText("ERROR");
                    tvValidationBadge.setTextColor(0xFFCC0000);
                    setBusy(false);
                    updateDebugInfo();
                    addLog("LLM error: " + e.getMessage());
                    Toast.makeText(this, "LLM 请求失败", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void sendToSdk(String[] messages) {
        // Normalize messages to ensure "version" field is present —
        // the C++ SDK silently fails to process updateComponents without it
        for (int i = 0; i < Math.min(2, messages.length); i++) {
            messages[i] = ensureVersionField(messages[i]);
        }

        surfaceManager.beginTextStream();
        addLog("beginTextStream");

        surfaceManager.receiveTextChunk(messages[0]);
        addLog("Sent createSurface: " + messages[0].substring(0, Math.min(80, messages[0].length())));

        surfaceManager.receiveTextChunk(messages[1]);
        addLog("Sent updateComponents: " + messages[1].substring(0, Math.min(80, messages[1].length())));

        if (!messages[2].equals("{}")) {
            surfaceManager.receiveTextChunk(messages[2]);
            addLog("Sent updateDataModel");
        }

        surfaceManager.endTextStream();
        renderStatus = "rendered";
        setBusy(false);
        addLog("endTextStream - render complete");
        updateDebugInfo();
        Toast.makeText(this, "UI 渲染完成", Toast.LENGTH_SHORT).show();
    }

    private String ensureVersionField(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            if (!obj.has("version")) {
                obj.put("version", "v0.9");
                addLog("Normalized: added missing 'version' field");
                return obj.toString();
            }
        } catch (Exception e) {
            addLog("ensureVersionField: parse failed for: " + json.substring(0, Math.min(40, json.length())));
        }
        return json;
    }

    private void streamToSdk(String[] messages) {
        addLog("Streaming mode: chunkSize=" + streamingSimulator.getChunkSize()
                + " delayMs=" + streamingSimulator.getDelayMs());

        streamingSimulator.stream(messages, new StreamingSimulator.StreamCallback() {
            @Override
            public void onChunkSent(int chunkIndex, int totalChunks, String chunkPreview) {
                runOnUiThread(() -> addLog("Chunk " + (chunkIndex + 1) + "/" + totalChunks
                        + ": " + chunkPreview));
            }

            @Override
            public void onStreamComplete() {
                runOnUiThread(() -> {
                    renderStatus = "rendered";
                    setBusy(false);
                    updateDebugInfo();
                    addLog("Streaming complete");
                    Toast.makeText(TextDemoActivity.this,
                            "UI 渲染完成 (streaming)", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onStreamError(Exception e) {
                runOnUiThread(() -> {
                    renderStatus = "error";
                    lastError = Log.getStackTraceString(e);
                    setBusy(false);
                    updateDebugInfo();
                    addLog("Streaming error: " + e.getMessage());
                });
            }
        });
    }

    private void handleLlmSseGenerate(String input, String baseUrl) {
        String sseUrl = LLMUiGenerationProvider.deriveStreamUrl(baseUrl);
        String proxyToken = etProxyToken.getText().toString().trim();

        setBusy(true);
        lastUserInput = input;
        lastRawLlmOutput = "";
        lastError = "";
        lastGeneratedMessages = null;
        renderStatus = "generating";
        sseStatus = "connecting";
        sseChunkCount = 0;
        sseLastChunkPreview = "";
        sseAccumulatedText = "";
        btnGenerate.setText("生成中...");
        tvValidationBadge.setText("WAIT");
        tvValidationBadge.setTextColor(0xFF996600);
        updateDebugInfo();
        addLog("SSE request to: " + sseUrl);

        sseClient = new SSEClient();

        String jsonBody;
        try {
            jsonBody = LLMUiGenerationProvider.buildRequestBodyJson(input);
        } catch (Exception e) {
            renderStatus = "error";
            lastError = "Failed to build request: " + e.getMessage();
            tvValidationBadge.setText("ERROR");
            tvValidationBadge.setTextColor(0xFFCC0000);
            setBusy(false);
            sseStatus = "error";
            updateDebugInfo();
            return;
        }

        SSEClient.SSECallback callback = new SSEClient.SSECallback() {
            @Override
            public void onConnected() {
                mainHandler.post(() -> {
                    sseStatus = "streaming";
                    renderStatus = "streaming";
                    surfaceManager.beginTextStream();
                    addLog("SSE connected, beginTextStream");
                    updateDebugInfo();
                });
            }

            @Override
            public void onChunk(String delta, int chunkIndex, String preview) {
                mainHandler.post(() -> {
                    surfaceManager.receiveTextChunk(delta);
                    sseChunkCount = chunkIndex;
                    sseLastChunkPreview = preview;
                    sseAccumulatedText = sseClient.getAccumulatedText();
                    if (chunkIndex % 10 == 0) {
                        addLog("SSE chunk #" + chunkIndex + ": " + preview);
                    }
                    updateDebugInfo();
                });
            }

            @Override
            public void onComplete(String fullText) {
                mainHandler.post(() -> {
                    surfaceManager.endTextStream();
                    sseStatus = "complete";
                    sseAccumulatedText = fullText;
                    lastRawLlmOutput = fullText;
                    addLog("SSE complete, " + sseChunkCount + " chunks received");

                    // Post-stream validation
                    A2uiJsonValidator.ValidationResult vr =
                            A2uiJsonValidator.validateFromRawText(fullText);
                    if (!vr.isValid()) {
                        renderStatus = "error";
                        lastError = vr.getFormattedReport();
                        tvValidationBadge.setText("FAIL");
                        tvValidationBadge.setTextColor(0xFFCC0000);
                        addLog("SSE post-stream validation failed: "
                                + vr.getErrors().size() + " error(s)");
                        Toast.makeText(TextDemoActivity.this,
                                "SSE 输出验证失败，请查看 Debug 面板", Toast.LENGTH_LONG).show();
                    } else {
                        tvValidationBadge.setText("PASS");
                        tvValidationBadge.setTextColor(0xFF008800);
                        lastGeneratedMessages = parseMessagesFromRawText(fullText);
                        renderStatus = "rendered";
                        if (!vr.getWarnings().isEmpty()) {
                            addLog("Validation warnings: " + vr.getWarnings().size());
                        }
                    }

                    setBusy(false);
                    updateDebugInfo();
                });
            }

            @Override
            public void onError(Exception e, String partialText) {
                mainHandler.post(() -> {
                    try { surfaceManager.endTextStream(); } catch (Exception ignored) {}
                    sseStatus = "error";
                    sseAccumulatedText = partialText;
                    lastRawLlmOutput = partialText;

                    if (e.getMessage() != null && e.getMessage().contains("Cancelled")) {
                        addLog("SSE cancelled");
                    } else {
                        renderStatus = "error";
                        lastError = e.getMessage() != null ? e.getMessage()
                                : Log.getStackTraceString(e);
                        tvValidationBadge.setText("ERROR");
                        tvValidationBadge.setTextColor(0xFFCC0000);
                        addLog("SSE error: " + lastError);
                        Toast.makeText(TextDemoActivity.this,
                                "SSE 错误: " + lastError, Toast.LENGTH_LONG).show();
                    }
                    setBusy(false);
                    updateDebugInfo();
                });
            }
        };

        llmExecutor.execute(() -> sseClient.connect(sseUrl, jsonBody, proxyToken, callback));
    }

    private String[] parseMessagesFromRawText(String rawText) {
        String text = rawText.trim();
        if (text.startsWith("```")) {
            String[] lines = text.split("\n");
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                if (!line.trim().startsWith("```")) {
                    sb.append(line).append("\n");
                }
            }
            text = sb.toString().trim();
        }
        try {
            JSONArray arr = new JSONArray(text);
            String[] messages = new String[3];
            for (int i = 0; i < 3; i++) {
                if (i < arr.length()) {
                    Object item = arr.get(i);
                    messages[i] = item instanceof JSONObject
                            ? ((JSONObject) item).toString() : item.toString();
                } else {
                    messages[i] = "{}";
                }
            }
            return messages;
        } catch (Exception e) {
            // After SSE JsonArrayStripper, text may be {obj1}{obj2}{obj3} without [ ] wrapper.
            // Only try parsing as concatenated objects if text starts with '{'.
            if (text.startsWith("{")) {
                try {
                    JSONArray arr = A2uiJsonValidator.parseConcatenatedObjectsAsArray(text);
                    String[] messages = new String[3];
                    for (int i = 0; i < 3; i++) {
                        if (i < arr.length()) {
                            Object item = arr.get(i);
                            messages[i] = item instanceof JSONObject
                                    ? ((JSONObject) item).toString() : item.toString();
                        } else {
                            messages[i] = "{}";
                        }
                    }
                    return messages;
                } catch (Exception e2) {
                    return new String[]{"{}", "{}", "{}"};
                }
            }
            return new String[]{"{}", "{}", "{}"};
        }
    }

    private void toggleDebug() {
        debugExpanded = !debugExpanded;
        if (debugExpanded) {
            debugScrollView.setVisibility(View.VISIBLE);
            tvDebugToggle.setText("▲");
        } else {
            debugScrollView.setVisibility(View.GONE);
            tvDebugToggle.setText("▼");
        }
    }

    private void toggleLogs() {
        logsExpanded = !logsExpanded;
        if (logsExpanded) {
            logsScrollView.setVisibility(View.VISIBLE);
            tvLogsToggle.setText("▲");
        } else {
            logsScrollView.setVisibility(View.GONE);
            tvLogsToggle.setText("▼");
        }
    }

    private void setBusy(boolean busy) {
        isBusy = busy;
        btnGenerate.setEnabled(!busy);
        btnGenerate.setText(busy ? "生成中..." : "生成 UI");
    }

    private void cancelActiveStream() {
        if (streamingSimulator != null) {
            streamingSimulator.cancel();
        }
        if (sseClient != null) {
            sseClient.cancel();
            sseClient = null;
        }
        if (isBusy) {
            setBusy(false);
            renderStatus = "idle";
            updateDebugInfo();
        }
    }

    private void updateDebugInfo() {
        String providerInfo = "Provider: " + currentProviderType.getDisplayName();
        if (currentProviderType == ProviderType.FIXTURE && fixtureProvider.getSelectedFixture() != null) {
            providerInfo += " [" + fixtureProvider.getSelectedFixture() + "]";
        }
        if (currentProviderType == ProviderType.LLM) {
            providerInfo += " [" + llmProvider.getEndpointUrl() + "]";
        }
        tvDebugProvider.setText(providerInfo);
        tvDebugInput.setText("Input: " + (lastUserInput.isEmpty() ? "-" : lastUserInput));

        boolean isSseActive = currentProviderType == ProviderType.LLM && streamingMode;
        if (isSseActive && "streaming".equals(renderStatus)) {
            tvDebugRenderStatus.setText("Render: streaming (SSE)");
        } else {
            tvDebugRenderStatus.setText("Render: " + renderStatus);
        }

        // SSE debug views
        if (isSseActive && !sseStatus.isEmpty()) {
            tvDebugSseStatus.setVisibility(View.VISIBLE);
            tvDebugSseStatus.setText("SSE: " + sseStatus
                    + (sseChunkCount > 0 ? " (" + sseChunkCount + " chunks)" : ""));

            tvDebugSseLastChunk.setVisibility(View.VISIBLE);
            tvDebugSseLastChunk.setText("Last chunk: " + sseLastChunkPreview);

            tvDebugSseAccumulated.setVisibility(View.VISIBLE);
            tvDebugSseAccumulated.setText(sseAccumulatedText);
        } else {
            tvDebugSseStatus.setVisibility(View.GONE);
            tvDebugSseLastChunk.setVisibility(View.GONE);
            tvDebugSseAccumulated.setVisibility(View.GONE);
        }

        if (lastGeneratedMessages != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("// createSurface\n").append(prettifyJson(lastGeneratedMessages[0]));
            sb.append("\n\n// updateComponents\n").append(prettifyJson(lastGeneratedMessages[1]));
            if (!"{}".equals(lastGeneratedMessages[2])) {
                sb.append("\n\n// updateDataModel\n").append(prettifyJson(lastGeneratedMessages[2]));
            }
            tvDebugRawJson.setText(sb.toString());
        } else if (!lastRawLlmOutput.isEmpty()) {
            tvDebugRawJson.setText("// LLM Raw Output\n" + prettifyJson(lastRawLlmOutput));
        } else {
            tvDebugRawJson.setText("");
        }

        if (!lastError.isEmpty()) {
            tvDebugErrors.setText(lastError);
            tvDebugErrors.setVisibility(View.VISIBLE);
        } else {
            tvDebugErrors.setVisibility(View.GONE);
        }
    }

    private String prettifyJson(String json) {
        try {
            return new JSONObject(json).toString(2);
        } catch (Exception e) {
            return json;
        }
    }

    private void addLog(String message) {
        Log.d(TAG, message);

        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        String logEntry = "[" + timestamp + "] " + message;

        TextView logView = new TextView(this);
        logView.setText(logEntry);
        logView.setTextSize(11);
        logView.setTextColor(0xFF666666);
        logView.setPadding(4, 4, 4, 4);

        // Remove "No logs yet" hint
        if (logsContent.getChildCount() == 1) {
            TextView firstChild = (TextView) logsContent.getChildAt(0);
            if (firstChild.getText().toString().equals("No logs yet")) {
                logsContent.removeAllViews();
            }
        }

        logsContent.addView(logView, 0);

        // Limit to 30 log entries
        while (logsContent.getChildCount() > 30) {
            logsContent.removeViewAt(logsContent.getChildCount() - 1);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelActiveStream();
        llmExecutor.shutdownNow();
        if (surfaceManager != null) {
            try {
                surfaceManager.destroy();
            } catch (Exception e) {
                Log.e(TAG, "Failed to destroy SurfaceManager", e);
            }
        }
    }
}
