package com.amap.agenuidemo.card;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CardTemplateRendererTest {

    @Test
    public void render_textSummary_returnsValidA2ui() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "test_summary")
                .put("cardType", "text_summary")
                .put("title", "今日天气")
                .put("content", "晴转多云，26°C")
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());

        String[] messages = result.getMessages();
        assertEquals(3, messages.length);

        // createSurface must have correct surfaceId
        JSONObject create = new JSONObject(messages[0]);
        assertEquals("v0.9", create.getString("version"));
        assertTrue(create.getJSONObject("createSurface").getString("surfaceId").startsWith("card_"));

        // updateComponents must have root component
        JSONObject update = new JSONObject(messages[1]);
        JSONArray components = update.getJSONObject("updateComponents").getJSONArray("components");
        assertEquals("root", components.getJSONObject(0).getString("id"));
        assertEquals("Column", components.getJSONObject(0).getString("component"));

        // Title text must be present
        boolean foundTitle = false;
        for (int i = 0; i < components.length(); i++) {
            JSONObject comp = components.getJSONObject(i);
            if ("title-text".equals(comp.getString("id"))) {
                assertEquals("今日天气", comp.getString("text"));
                foundTitle = true;
            }
        }
        assertTrue(foundTitle);

        // 3rd message should be empty data model
        assertEquals("{}", messages[2]);
    }

    @Test
    public void render_textList_returnsValidA2ui() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "test_list")
                .put("cardType", "text_list")
                .put("title", "功能列表")
                .put("items", new JSONArray()
                        .put(new JSONObject().put("text", "天气"))
                        .put(new JSONObject().put("text", "导航"))
                        .put(new JSONObject().put("text", "语音")))
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertTrue(result.isValid());

        JSONObject update = new JSONObject(result.getMessages()[1]);
        JSONArray components = update.getJSONObject("updateComponents").getJSONArray("components");

        // root + title + list + 3 items = 6 components
        assertEquals(6, components.length());

        // root must contain title-text and item-list
        JSONObject root = components.getJSONObject(0);
        assertEquals("root", root.getString("id"));
        JSONArray rootChildren = root.getJSONArray("children");
        assertEquals(2, rootChildren.length());
        assertEquals("title-text", rootChildren.getString(0));
        assertEquals("item-list", rootChildren.getString(1));

        // List must reference 3 items
        JSONObject list = null;
        for (int i = 0; i < components.length(); i++) {
            if ("item-list".equals(components.getJSONObject(i).getString("id"))) {
                list = components.getJSONObject(i);
                break;
            }
        }
        assertNotNull(list);
        assertEquals(3, list.getJSONArray("children").length());

        // Each item text should match
        assertEquals("天气", components.getJSONObject(3).getString("text"));
        assertEquals("导航", components.getJSONObject(4).getString("text"));
        assertEquals("语音", components.getJSONObject(5).getString("text"));
    }

    @Test
    public void render_imageTextList_returnsValidA2ui() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "test_imglist")
                .put("cardType", "image_text_list")
                .put("title", "热门推荐")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("imageUrl", "https://example.com/1.jpg")
                                .put("title", "助手")
                                .put("subtitle", "随时解答")))
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertTrue(result.isValid());

        JSONObject update = new JSONObject(result.getMessages()[1]);
        JSONArray components = update.getJSONObject("updateComponents").getJSONArray("components");

        // root + title + card + row + image + col + title_text + subtitle = 8
        assertEquals(8, components.length());

        // Check Card references row as child
        JSONObject card = null;
        for (int i = 0; i < components.length(); i++) {
            if ("img_card_0".equals(components.getJSONObject(i).getString("id"))) {
                card = components.getJSONObject(i);
                break;
            }
        }
        assertNotNull(card);
        assertEquals("img_row_0", card.getString("child"));

        // Check Image src
        JSONObject image = null;
        for (int i = 0; i < components.length(); i++) {
            if ("img_img_0".equals(components.getJSONObject(i).getString("id"))) {
                image = components.getJSONObject(i);
                break;
            }
        }
        assertNotNull(image);
        assertEquals("https://example.com/1.jpg", image.getString("src"));
    }

    @Test
    public void render_invalidCardType_returnsFallback() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "test_invalid")
                .put("cardType", "unknown_type")
                .put("title", "Broken")
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Unknown cardType")));

        // Fallback must still have 3 messages
        String[] messages = result.getMessages();
        assertEquals(3, messages.length);

        // Fallback must have root component
        JSONObject update = new JSONObject(messages[1]);
        JSONArray components = update.getJSONObject("updateComponents").getJSONArray("components");
        assertEquals("root", components.getJSONObject(0).getString("id"));
    }

    @Test
    public void render_emptyItems_returnsFallback() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "test_empty")
                .put("cardType", "text_list")
                .put("title", "空列表")
                .put("items", new JSONArray())
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("items")));

        // Fallback must have valid A2UI structure
        String[] messages = result.getMessages();
        assertEquals(3, messages.length);
        JSONObject update = new JSONObject(messages[1]);
        assertTrue(update.getJSONObject("updateComponents").getJSONArray("components").length() > 0);
    }

    @Test
    public void render_surfaceIdUsesRequestId() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "my_req_123")
                .put("cardType", "text_summary")
                .put("title", "Title")
                .put("content", "Content")
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        JSONObject create = new JSONObject(result.getMessages()[0]);
        String surfaceId = create.getJSONObject("createSurface").getString("surfaceId");
        assertEquals("card_my_req_123", surfaceId);

        // updateComponents must use the same surfaceId
        JSONObject update = new JSONObject(result.getMessages()[1]);
        assertEquals(surfaceId, update.getJSONObject("updateComponents").getString("surfaceId"));
    }

    @Test
    public void render_fallbackSurfaceIdIsStable() throws Exception {
        String cardData = "{}";
        CardRenderResult result = CardTemplateRenderer.render(cardData);
        JSONObject create = new JSONObject(result.getMessages()[0]);
        String surfaceId = create.getJSONObject("createSurface").getString("surfaceId");
        assertEquals("card_fallback", surfaceId);
    }
}