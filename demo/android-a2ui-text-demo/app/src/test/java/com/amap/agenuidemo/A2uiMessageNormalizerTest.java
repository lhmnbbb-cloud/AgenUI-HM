package com.amap.agenuidemo;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class A2uiMessageNormalizerTest {

    @Test
    public void normalizeRawText_messagesWrapperWithTypeAndMissingVersion_returnsValidMessages() throws Exception {
        JSONArray messages = new JSONArray()
                .put(new JSONObject()
                        .put("createSurface", new JSONObject()
                                .put("surfaceId", "s1")))
                .put(new JSONObject()
                        .put("updateComponents", new JSONObject()
                                .put("surfaceId", "s1")
                                .put("components", new JSONArray()
                                        .put(new JSONObject()
                                                .put("id", "root")
                                                .put("type", "Column")
                                                .put("children", new JSONArray().put("title")))
                                        .put(new JSONObject()
                                                .put("id", "title")
                                                .put("type", "Text")
                                                .put("content", "Hello")))));

        String raw = new JSONObject().put("messages", messages).toString();

        String[] normalized = A2uiMessageNormalizer.normalizeRawText(raw);

        JSONObject create = new JSONObject(normalized[0]);
        JSONObject update = new JSONObject(normalized[1]);
        JSONArray components = update.getJSONObject("updateComponents").getJSONArray("components");

        assertEquals("v0.9", create.getString("version"));
        assertEquals("v0.9", update.getString("version"));
        assertEquals("Column", components.getJSONObject(0).getString("component"));
        assertEquals("Text", components.getJSONObject(1).getString("component"));
        assertEquals("Hello", components.getJSONObject(1).getString("text"));
        assertTrue(A2uiJsonValidator.validate(normalized[0], normalized[1], normalized[2]).isValid());
    }

    @Test
    public void normalizeRawText_nestedComponents_flattensChildrenToIds() throws Exception {
        JSONArray components = new JSONArray()
                .put(new JSONObject()
                        .put("id", "root")
                        .put("component", "Column")
                        .put("children", new JSONArray()
                                .put(new JSONObject()
                                        .put("id", "child-text")
                                        .put("component", "Text")
                                        .put("text", "Nested"))));

        String[] normalized = A2uiMessageNormalizer.normalizeRawText(components.toString());
        JSONObject update = new JSONObject(normalized[1]);
        JSONArray flat = update.getJSONObject("updateComponents").getJSONArray("components");

        assertEquals(2, flat.length());
        assertEquals("child-text",
                flat.getJSONObject(1).getJSONArray("children").getString(0));
        assertTrue(A2uiJsonValidator.validate(normalized[0], normalized[1], normalized[2]).isValid());
    }

    @Test
    public void normalizeRawText_multipleRootComponents_wrapsWithRootColumn() throws Exception {
        JSONArray components = new JSONArray()
                .put(new JSONObject().put("id", "a").put("component", "Text").put("text", "A"))
                .put(new JSONObject().put("id", "b").put("component", "Text").put("text", "B"));

        String[] normalized = A2uiMessageNormalizer.normalizeRawText(components.toString());
        JSONObject update = new JSONObject(normalized[1]);
        JSONArray flat = update.getJSONObject("updateComponents").getJSONArray("components");

        assertEquals("root", flat.getJSONObject(0).getString("id"));
        assertEquals(2, flat.getJSONObject(0).getJSONArray("children").length());
        assertTrue(A2uiJsonValidator.validate(normalized[0], normalized[1], normalized[2]).isValid());
    }

    @Test
    public void normalizeRawText_buttonText_addsTextChild() throws Exception {
        JSONArray components = new JSONArray()
                .put(new JSONObject()
                        .put("id", "root")
                        .put("component", "Button")
                        .put("text", "Submit"));

        String[] normalized = A2uiMessageNormalizer.normalizeRawText(components.toString());
        JSONObject update = new JSONObject(normalized[1]);
        JSONArray flat = update.getJSONObject("updateComponents").getJSONArray("components");

        assertEquals("root_label", flat.getJSONObject(1).getString("id"));
        assertEquals("root_label", flat.getJSONObject(0).getString("child"));
        assertTrue(A2uiJsonValidator.validate(normalized[0], normalized[1], normalized[2]).isValid());
    }
}
