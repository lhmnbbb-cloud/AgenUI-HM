package com.amap.agenuidemo;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class LLMUiGenerationProviderTest {

    // --- deriveStreamUrl ---

    @Test
    public void deriveStreamUrl_null_returnsNull() {
        assertNull(LLMUiGenerationProvider.deriveStreamUrl(null));
    }

    @Test
    public void deriveStreamUrl_generateUi_replacesSuffix() {
        String result = LLMUiGenerationProvider.deriveStreamUrl("http://10.0.2.2:8787/generate-ui");
        assertEquals("http://10.0.2.2:8787/generate-ui-stream", result);
    }

    @Test
    public void deriveStreamUrl_customPath_appendsStream() {
        String result = LLMUiGenerationProvider.deriveStreamUrl("http://example.com/api/gen");
        assertEquals("http://example.com/api/gen-stream", result);
    }

    @Test
    public void deriveStreamUrl_trailingSlash_generateUi_replacesSuffix() {
        String result = LLMUiGenerationProvider.deriveStreamUrl("http://host:8787/generate-ui");
        assertEquals("http://host:8787/generate-ui-stream", result);
    }

    @Test
    public void deriveStreamUrl_noPath_appendsStream() {
        String result = LLMUiGenerationProvider.deriveStreamUrl("http://host:8787");
        assertEquals("http://host:8787-stream", result);
    }

    // --- buildRequestBodyJson ---

    @Test
    public void buildRequestBodyJson_containsUserInput() throws Exception {
        String json = LLMUiGenerationProvider.buildRequestBodyJson("show weather");
        JSONObject obj = new JSONObject(json);
        assertEquals("show weather", obj.getString("userInput"));
    }

    @Test
    public void buildRequestBodyJson_nullUserInput_becomesEmpty() throws Exception {
        String json = LLMUiGenerationProvider.buildRequestBodyJson(null);
        JSONObject obj = new JSONObject(json);
        assertEquals("", obj.getString("userInput"));
    }

    @Test
    public void buildRequestBodyJson_hasVersion() throws Exception {
        String json = LLMUiGenerationProvider.buildRequestBodyJson("test");
        JSONObject obj = new JSONObject(json);
        assertEquals("v0.9", obj.getString("version"));
    }

    @Test
    public void buildRequestBodyJson_has22Components() throws Exception {
        String json = LLMUiGenerationProvider.buildRequestBodyJson("test");
        JSONObject obj = new JSONObject(json);
        JSONArray comps = obj.getJSONArray("availableComponents");
        assertEquals(22, comps.length());
    }

    @Test
    public void buildRequestBodyJson_includesButton() throws Exception {
        String json = LLMUiGenerationProvider.buildRequestBodyJson("test");
        assertTrue(json.contains("Button"));
    }

    @Test
    public void buildRequestBodyJson_isValidJson() throws Exception {
        String json = LLMUiGenerationProvider.buildRequestBodyJson("hello");
        // Should not throw
        new JSONObject(json);
    }
}
