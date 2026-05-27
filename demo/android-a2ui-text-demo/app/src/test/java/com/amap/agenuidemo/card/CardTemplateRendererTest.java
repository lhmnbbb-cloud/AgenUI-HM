package com.amap.agenuidemo.card;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

import com.amap.agenuidemo.A2uiJsonValidator;

public class CardTemplateRendererTest {

    /**
     * Helper: asserts that all three A2UI messages pass A2uiJsonValidator.
     * This locks in the guarantee that template output is always valid A2UI.
     */
    private void assertPassesA2uiValidator(String[] messages) {
        A2uiJsonValidator.ValidationResult vr =
                A2uiJsonValidator.validate(messages[0], messages[1], messages[2]);
        assertTrue("Template output must pass A2uiJsonValidator. Errors: "
                        + vr.getFormattedReport(),
                vr.isValid());
    }

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

        assertPassesA2uiValidator(messages);
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

        assertPassesA2uiValidator(result.getMessages());
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

        assertPassesA2uiValidator(result.getMessages());
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

        assertPassesA2uiValidator(messages);
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

        assertPassesA2uiValidator(messages);
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

        assertPassesA2uiValidator(result.getMessages());
    }

    @Test
    public void render_fallbackSurfaceIdIsStable() throws Exception {
        String cardData = "{}";
        CardRenderResult result = CardTemplateRenderer.render(cardData);
        JSONObject create = new JSONObject(result.getMessages()[0]);
        String surfaceId = create.getJSONObject("createSurface").getString("surfaceId");
        assertEquals("card_fallback", surfaceId);

        assertPassesA2uiValidator(result.getMessages());
    }

    @Test
    public void render_textSummaryWithWarnings_propagatesWarnings() throws Exception {
        // text_list with empty text in item should produce a warning
        String cardData = new JSONObject()
                .put("requestId", "test_warn")
                .put("cardType", "text_list")
                .put("title", "警告测试")
                .put("items", new JSONArray()
                        .put(new JSONObject().put("text", "正常项"))
                        .put(new JSONObject().put("text", "")))
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("empty") && w.contains("text")));

        assertPassesA2uiValidator(result.getMessages());
    }

    @Test
    public void render_imageTextListMissingSubtitle_producesWarning() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "test_warn_img")
                .put("cardType", "image_text_list")
                .put("title", "带警告的图文")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("imageUrl", "https://example.com/1.jpg")
                                .put("title", "有标题")))
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("subtitle")));

        assertPassesA2uiValidator(result.getMessages());
    }

    @Test
    public void render_sportsScoreList_returnsValidA2ui() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "nba_render")
                .put("cardType", "sports_score_list")
                .put("title", "NBA 今日赛况")
                .put("subtitle", "2026-05-26")
                .put("updatedAt", "14:30 更新")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("status", "final")
                                .put("summary", "湖人终结连败")
                                .put("homeTeam", new JSONObject().put("name", "湖人").put("score", 108))
                                .put("awayTeam", new JSONObject().put("name", "凯尔特人").put("score", 102)))
                        .put(new JSONObject()
                                .put("status", "scheduled")
                                .put("startTime", "19:00")
                                .put("homeTeam", new JSONObject().put("name", "雄鹿"))
                                .put("awayTeam", new JSONObject().put("name", "76人"))))
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());

        String[] messages = result.getMessages();
        assertEquals(3, messages.length);

        // createSurface
        JSONObject create = new JSONObject(messages[0]);
        assertEquals("card_nba_render", create.getJSONObject("createSurface").getString("surfaceId"));

        // updateComponents
        JSONObject update = new JSONObject(messages[1]);
        JSONArray components = update.getJSONObject("updateComponents").getJSONArray("components");

        // root + title + subtitle + card0(12: card+col+statusRow+statusText+summaryText+scoreRow+awayCol+awayName+awayScore+homeCol+homeName+homeScore) + card1(11: card+col+statusRow+statusText+scoreRow+awayCol+awayName+awayScore+homeCol+homeName+homeScore)
        // = 3 + 12 + 11 = 26
        assertEquals(26, components.length());

        // Verify root has title-text, subtitle-text, game_card_0, game_card_1
        JSONObject root = components.getJSONObject(0);
        JSONArray rootChildren = root.getJSONArray("children");
        assertEquals(4, rootChildren.length());
        assertEquals("title-text", rootChildren.getString(0));
        assertEquals("subtitle-text", rootChildren.getString(1));
        assertEquals("game_card_0", rootChildren.getString(2));
        assertEquals("game_card_1", rootChildren.getString(3));

        // Verify status display for first game (final)
        JSONObject statusText0 = findComponentById(components, "status_text_0");
        assertNotNull(statusText0);
        assertEquals("FINAL", statusText0.getString("text"));

        // Verify score display for first game
        JSONObject awayScore0 = findComponentById(components, "away_score_0");
        assertNotNull(awayScore0);
        assertEquals("102", awayScore0.getString("text"));

        // Verify status display for second game (scheduled with startTime)
        JSONObject statusText1 = findComponentById(components, "status_text_1");
        assertNotNull(statusText1);
        assertEquals("19:00 开赛", statusText1.getString("text"));

        // Verify score display for scheduled game (no score)
        JSONObject homeScore1 = findComponentById(components, "home_score_1");
        assertNotNull(homeScore1);
        assertEquals("-", homeScore1.getString("text"));

        assertPassesA2uiValidator(messages);
    }

    @Test
    public void render_sportsScoreListEmptyItems_returnsEmptyState() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "nba_empty")
                .put("cardType", "sports_score_list")
                .put("title", "NBA 今日赛况")
                .put("items", new JSONArray())
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertTrue(result.isValid());

        JSONObject update = new JSONObject(result.getMessages()[1]);
        JSONArray components = update.getJSONObject("updateComponents").getJSONArray("components");

        // root + title + empty-card + empty-content + empty-msg = 5
        assertEquals(5, components.length());

        // Verify empty message text
        JSONObject emptyMsg = findComponentById(components, "empty-msg");
        assertNotNull(emptyMsg);
        assertEquals("今日暂无赛况", emptyMsg.getString("text"));

        assertPassesA2uiValidator(result.getMessages());
    }

    @Test
    public void render_sportsScoreListWithWarnings_propagatesWarnings() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "nba_warn")
                .put("cardType", "sports_score_list")
                .put("title", "NBA")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("status", "postponed")
                                .put("homeTeam", new JSONObject().put("name", "湖人"))
                                .put("awayTeam", new JSONObject().put("name", "凯尔特人"))))
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("unknown status")));

        assertPassesA2uiValidator(result.getMessages());
    }

    @Test
    public void render_sportsScoreListPartialFields_rendersGracefully() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "nba_partial")
                .put("cardType", "sports_score_list")
                .put("title", "NBA 今日赛况")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("status", "final")
                                .put("homeTeam", new JSONObject().put("name", "湖人").put("score", 108))
                                .put("awayTeam", new JSONObject().put("name", "凯尔特人").put("score", 102)))
                        .put(new JSONObject()
                                .put("status", "scheduled")
                                .put("homeTeam", new JSONObject().put("name", "雄鹿"))
                                .put("awayTeam", new JSONObject().put("name", "76人"))))
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertTrue(result.isValid());

        JSONObject update = new JSONObject(result.getMessages()[1]);
        JSONArray components = update.getJSONObject("updateComponents").getJSONArray("components");

        // No subtitle line (no subtitle/updatedAt fields)
        JSONObject root = components.getJSONObject(0);
        JSONArray rootChildren = root.getJSONArray("children");
        assertEquals(3, rootChildren.length()); // title-text + game_card_0 + game_card_1

        // No summary text for game 0 (summary not provided)
        assertNull(findComponentById(components, "summary_text_0"));

        // Status for scheduled game without startTime → "待开赛"
        JSONObject statusText1 = findComponentById(components, "status_text_1");
        assertNotNull(statusText1);
        assertEquals("待开赛", statusText1.getString("text"));

        assertPassesA2uiValidator(result.getMessages());
    }

    @Test
    public void render_sportsScoreListNullAndEmptyScore_showsDash() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "nba_null")
                .put("cardType", "sports_score_list")
                .put("title", "NBA")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("status", "final")
                                .put("homeTeam", new JSONObject().put("name", "湖人").put("score", JSONObject.NULL))
                                .put("awayTeam", new JSONObject().put("name", "凯尔特人").put("score", ""))))
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertTrue(result.isValid());

        JSONObject update = new JSONObject(result.getMessages()[1]);
        JSONArray components = update.getJSONObject("updateComponents").getJSONArray("components");

        // null score → "--" for final
        JSONObject homeScore0 = findComponentById(components, "home_score_0");
        assertNotNull(homeScore0);
        assertEquals("--", homeScore0.getString("text"));

        // empty string score → "--" for final
        JSONObject awayScore0 = findComponentById(components, "away_score_0");
        assertNotNull(awayScore0);
        assertEquals("--", awayScore0.getString("text"));

        assertPassesA2uiValidator(result.getMessages());
    }

    private static JSONObject findComponentById(JSONArray components, String id) throws Exception {
        for (int i = 0; i < components.length(); i++) {
            JSONObject comp = components.getJSONObject(i);
            if (id.equals(comp.optString("id"))) {
                return comp;
            }
        }
        return null;
    }

    @Test
    public void render_weatherSummary_returnsValidA2ui() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "weather_render")
                .put("cardType", "weather_summary")
                .put("title", "今日天气")
                .put("location", "上海")
                .put("updatedAt", "14:30 更新")
                .put("current", new JSONObject()
                        .put("condition", "晴转多云")
                        .put("temperature", 26)
                        .put("high", 30)
                        .put("low", 18)
                        .put("airQuality", "良")
                        .put("humidity", "65%")
                        .put("wind", "东南风 3级"))
                .put("tips", new JSONArray()
                        .put("紫外线较强，注意防晒")
                        .put("早晚温差较大，注意添衣"))
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());

        String[] messages = result.getMessages();
        assertEquals(3, messages.length);

        JSONObject create = new JSONObject(messages[0]);
        assertEquals("card_weather_render", create.getJSONObject("createSurface").getString("surfaceId"));

        JSONObject update = new JSONObject(messages[1]);
        JSONArray components = update.getJSONObject("updateComponents").getJSONArray("components");
        assertEquals(18, components.length());

        assertPassesA2uiValidator(messages);
    }

    @Test
    public void render_weatherSummaryPartial_rendersGracefully() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "weather_partial")
                .put("cardType", "weather_summary")
                .put("title", "天气")
                .put("current", new JSONObject()
                        .put("condition", "晴")
                        .put("temperature", 28))
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertTrue(result.isValid());

        JSONObject update = new JSONObject(result.getMessages()[1]);
        JSONArray components = update.getJSONObject("updateComponents").getJSONArray("components");
        assertEquals(7, components.length());

        assertNull(findComponentById(components, "location-time-text"));
        assertNull(findComponentById(components, "highlow-row"));
        assertNull(findComponentById(components, "detail-row"));

        assertPassesA2uiValidator(result.getMessages());
    }

    @Test
    public void render_weatherSummaryWithWarnings_propagatesWarnings() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "weather_warn")
                .put("cardType", "weather_summary")
                .put("title", "天气")
                .put("current", new JSONObject()
                        .put("condition", "多云")
                        .put("high", 25)
                        .put("low", 15))
                .put("tips", new JSONArray()
                        .put("带伞").put("防晒").put("喝水").put("少外出").put("关窗").put("注意路况"))
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("location")));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("temperature")));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("exceeds")));

        assertPassesA2uiValidator(result.getMessages());
    }

    @Test
    public void render_weatherSummaryHighLowStringWithDegree_noDuplicate() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "weather_hl_degree")
                .put("cardType", "weather_summary")
                .put("title", "天气")
                .put("current", new JSONObject()
                        .put("condition", "晴")
                        .put("temperature", 26)
                        .put("high", "30°")
                        .put("low", "18°"))
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertTrue(result.isValid());

        JSONObject update = new JSONObject(result.getMessages()[1]);
        JSONArray components = update.getJSONObject("updateComponents").getJSONArray("components");

        JSONObject high = findComponentById(components, "high-text");
        assertNotNull(high);
        assertEquals("最高 30°", high.getString("text"));

        JSONObject low = findComponentById(components, "low-text");
        assertNotNull(low);
        assertEquals("最低 18°", low.getString("text"));

        assertPassesA2uiValidator(result.getMessages());
    }

    @Test
    public void render_weatherSummaryMinimalData_showsPlaceholder() throws Exception {
        String cardData = new JSONObject()
                .put("requestId", "weather_minimal")
                .put("cardType", "weather_summary")
                .put("title", "天气")
                .put("current", new JSONObject())
                .toString();

        CardRenderResult result = CardTemplateRenderer.render(cardData);
        assertTrue(result.isValid());

        JSONObject update = new JSONObject(result.getMessages()[1]);
        JSONArray components = update.getJSONObject("updateComponents").getJSONArray("components");
        // root + title + card + content + primary-row + condition-text(placeholder) = 6
        assertEquals(6, components.length());

        assertNull(findComponentById(components, "location-time-text"));
        assertNull(findComponentById(components, "highlow-row"));
        assertNull(findComponentById(components, "detail-row"));
        assertNull(findComponentById(components, "tips-col"));

        // Placeholder text when both condition and temperature are empty
        JSONObject condition = findComponentById(components, "condition-text");
        assertNotNull(condition);
        assertEquals("暂无天气数据", condition.getString("text"));

        assertNull(findComponentById(components, "temperature-text"));

        assertPassesA2uiValidator(result.getMessages());
    }
}
