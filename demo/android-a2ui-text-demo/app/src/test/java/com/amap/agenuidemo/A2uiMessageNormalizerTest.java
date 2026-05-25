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

    @Test
    public void normalizeRawText_singleRootNotNamedRoot_wrapsWithSyntheticRoot() throws Exception {
        JSONArray components = new JSONArray()
                .put(new JSONObject()
                        .put("id", "container")
                        .put("component", "Column")
                        .put("children", new JSONArray()
                                .put("header")
                                .put("temp")))
                .put(new JSONObject()
                        .put("id", "header")
                        .put("component", "Text")
                        .put("text", "上海天气"))
                .put(new JSONObject()
                        .put("id", "temp")
                        .put("component", "Text")
                        .put("text", "28°C"));

        String[] normalized = A2uiMessageNormalizer.normalizeRawText(components.toString());
        JSONObject update = new JSONObject(normalized[1]);
        JSONArray flat = update.getJSONObject("updateComponents").getJSONArray("components");

        // First component must be synthetic "root"
        JSONObject root = flat.getJSONObject(0);
        assertEquals("root", root.getString("id"));
        assertEquals("Column", root.getString("component"));
        JSONArray rootChildren = root.getJSONArray("children");
        assertEquals(1, rootChildren.length());
        assertEquals("container", rootChildren.getString(0));

        // Original "container" must still exist with its original id
        boolean foundContainer = false;
        for (int i = 0; i < flat.length(); i++) {
            if ("container".equals(flat.getJSONObject(i).getString("id"))) {
                foundContainer = true;
                break;
            }
        }
        assertTrue("Original 'container' component should be preserved", foundContainer);

        // Validator should pass
        assertTrue(A2uiJsonValidator.validate(normalized[0], normalized[1], normalized[2]).isValid());
    }

    @Test
    public void normalizeRawText_existingRootIsNotDoubleWrapped() throws Exception {
        JSONArray components = new JSONArray()
                .put(new JSONObject()
                        .put("id", "root")
                        .put("component", "Column")
                        .put("children", new JSONArray().put("txt1")))
                .put(new JSONObject()
                        .put("id", "txt1")
                        .put("component", "Text")
                        .put("text", "Hello"));

        String[] normalized = A2uiMessageNormalizer.normalizeRawText(components.toString());
        JSONObject update = new JSONObject(normalized[1]);
        JSONArray flat = update.getJSONObject("updateComponents").getJSONArray("components");

        // Should still have exactly 2 components — no extra synthetic root
        assertEquals(2, flat.length());
        assertEquals("root", flat.getJSONObject(0).getString("id"));

        assertTrue(A2uiJsonValidator.validate(normalized[0], normalized[1], normalized[2]).isValid());
    }

    @Test
    public void normalizeRawText_rootIdChildOfOtherComponent_renamesAndWraps() throws Exception {
        // "root" is a child of "container", not the topological root
        JSONArray components = new JSONArray()
                .put(new JSONObject()
                        .put("id", "container")
                        .put("component", "Column")
                        .put("children", new JSONArray().put("root")))
                .put(new JSONObject()
                        .put("id", "root")
                        .put("component", "Text")
                        .put("text", "Hello"));

        String[] normalized = A2uiMessageNormalizer.normalizeRawText(components.toString());

        JSONObject update = new JSONObject(normalized[1]);
        JSONArray flat = update.getJSONObject("updateComponents").getJSONArray("components");

        // Topological root must be id="root" (the synthetic wrapper)
        JSONObject topRoot = flat.getJSONObject(0);
        assertEquals("root", topRoot.getString("id"));
        assertEquals("Column", topRoot.getString("component"));

        // Synthetic root's children must contain "container"
        JSONArray rootChildren = topRoot.getJSONArray("children");
        assertEquals(1, rootChildren.length());
        assertEquals("container", rootChildren.getString(0));

        // The old "root" component must have been renamed (not collide with new "root")
        boolean foundRenamed = false;
        String renamedId = null;
        for (int i = 1; i < flat.length(); i++) {
            String id = flat.getJSONObject(i).getString("id");
            if (id.startsWith("root") && i > 0) {
                foundRenamed = true;
                renamedId = id;
            }
        }
        assertTrue("Old 'root' component should have been renamed", foundRenamed);

        // The "container" component should reference the renamed id, not "root"
        JSONObject containerComp = null;
        for (int i = 0; i < flat.length(); i++) {
            if ("container".equals(flat.getJSONObject(i).getString("id"))) {
                containerComp = flat.getJSONObject(i);
                break;
            }
        }
        assertNotNull(containerComp);
        String childRef = containerComp.getJSONArray("children").getString(0);
        assertEquals(renamedId, childRef);
        assertNotEquals("root", childRef);

        // Validator must pass
        assertTrue(A2uiJsonValidator.validate(normalized[0], normalized[1], normalized[2]).isValid());
    }
}
