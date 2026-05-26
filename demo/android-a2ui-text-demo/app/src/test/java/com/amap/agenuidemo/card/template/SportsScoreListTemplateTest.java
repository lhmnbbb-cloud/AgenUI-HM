package com.amap.agenuidemo.card.template;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

import com.amap.agenuidemo.A2uiJsonValidator;

public class SportsScoreListTemplateTest {

    private void assertPassesA2uiValidator(String[] messages) {
        A2uiJsonValidator.ValidationResult vr =
                A2uiJsonValidator.validate(messages[0], messages[1], messages[2]);
        assertTrue("Template output must pass A2uiJsonValidator. Errors: "
                        + vr.getFormattedReport(),
                vr.isValid());
    }

    @Test
    public void render_basic_returnsThreeMessages() throws Exception {
        JSONObject data = new JSONObject()
                .put("requestId", "test_basic")
                .put("cardType", "sports_score_list")
                .put("title", "NBA")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("status", "final")
                                .put("homeTeam", new JSONObject().put("name", "湖人").put("score", 108))
                                .put("awayTeam", new JSONObject().put("name", "凯尔特人").put("score", 102))));

        String[] messages = SportsScoreListTemplate.render(data);
        assertEquals(3, messages.length);
        assertEquals("{}", messages[2]);

        // createSurface
        JSONObject create = new JSONObject(messages[0]);
        assertEquals("card_test_basic", create.getJSONObject("createSurface").getString("surfaceId"));

        assertPassesA2uiValidator(messages);
    }

    @Test
    public void render_emptyItems_rendersEmptyState() throws Exception {
        JSONObject data = new JSONObject()
                .put("requestId", "test_empty")
                .put("cardType", "sports_score_list")
                .put("title", "NBA")
                .put("items", new JSONArray());

        String[] messages = SportsScoreListTemplate.render(data);
        JSONObject update = new JSONObject(messages[1]);
        JSONArray components = update.getJSONObject("updateComponents").getJSONArray("components");

        // root + title + empty-card + empty-content + empty-msg = 5
        assertEquals(5, components.length());

        JSONObject emptyMsg = findComponentById(components, "empty-msg");
        assertNotNull(emptyMsg);
        assertEquals("今日暂无赛况", emptyMsg.getString("text"));

        assertPassesA2uiValidator(messages);
    }

    @Test
    public void formatScore_number_returnsIntString() throws Exception {
        JSONObject team = new JSONObject().put("score", 108);
        assertEquals("108", SportsScoreListTemplate.formatScore(team, "final"));
    }

    @Test
    public void formatScore_null_final_returnsDashDash() throws Exception {
        JSONObject team = new JSONObject().put("score", JSONObject.NULL);
        assertEquals("--", SportsScoreListTemplate.formatScore(team, "final"));
    }

    @Test
    public void formatScore_null_scheduled_returnsSingleDash() throws Exception {
        JSONObject team = new JSONObject().put("score", JSONObject.NULL);
        assertEquals("-", SportsScoreListTemplate.formatScore(team, "scheduled"));
    }

    @Test
    public void formatScore_emptyString_final_returnsDashDash() throws Exception {
        JSONObject team = new JSONObject().put("score", "");
        assertEquals("--", SportsScoreListTemplate.formatScore(team, "final"));
    }

    @Test
    public void formatStatus_final() throws Exception {
        assertEquals("FINAL", SportsScoreListTemplate.formatStatus("final", ""));
    }

    @Test
    public void formatStatus_live() throws Exception {
        assertEquals("LIVE ●", SportsScoreListTemplate.formatStatus("live", ""));
    }

    @Test
    public void formatStatus_scheduled_withStartTime() throws Exception {
        assertEquals("19:00 开赛", SportsScoreListTemplate.formatStatus("scheduled", "19:00"));
    }

    @Test
    public void formatStatus_scheduled_noStartTime() throws Exception {
        assertEquals("待开赛", SportsScoreListTemplate.formatStatus("scheduled", ""));
    }

    @Test
    public void formatStatus_unknown() throws Exception {
        assertEquals("未知", SportsScoreListTemplate.formatStatus(null, ""));
        assertEquals("未知", SportsScoreListTemplate.formatStatus("", ""));
    }

    @Test
    public void formatStatus_unknownValue_passesThrough() throws Exception {
        assertEquals("postponed", SportsScoreListTemplate.formatStatus("postponed", ""));
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
}
