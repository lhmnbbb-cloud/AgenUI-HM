package com.amap.agenuidemo.card;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CardContractValidatorTest {

    @Test
    public void validate_validTextSummary_returnsValid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_001")
                .put("cardType", "text_summary")
                .put("title", "今日天气")
                .put("content", "晴转多云，26°C")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void validate_missingCardType_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_002")
                .put("title", "Test")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("cardType")));
    }

    @Test
    public void validate_unknownCardType_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_003")
                .put("cardType", "unknown_type")
                .put("title", "Test")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Unknown cardType")));
    }

    @Test
    public void validate_textSummaryMissingContent_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_004")
                .put("cardType", "text_summary")
                .put("title", "今日天气")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("content")));
    }

    @Test
    public void validate_validTextList_returnsValid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_005")
                .put("cardType", "text_list")
                .put("title", "功能列表")
                .put("items", new JSONArray()
                        .put(new JSONObject().put("text", "天气"))
                        .put(new JSONObject().put("text", "导航")))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
    }

    @Test
    public void validate_textListEmptyItems_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_006")
                .put("cardType", "text_list")
                .put("title", "空列表")
                .put("items", new JSONArray())
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("items")));
    }

    @Test
    public void validate_textListMissingItems_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_007")
                .put("cardType", "text_list")
                .put("title", "无列表项")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
    }

    @Test
    public void validate_validImageTextList_returnsValid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_008")
                .put("cardType", "image_text_list")
                .put("title", "热门推荐")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("imageUrl", "https://example.com/img1.jpg")
                                .put("title", "智能助手")
                                .put("subtitle", "随时解答"))
                        .put(new JSONObject()
                                .put("imageUrl", "https://example.com/img2.jpg")
                                .put("title", "出行规划")
                                .put("subtitle", "最优路线")))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
    }

    @Test
    public void validate_imageTextListMissingImageUrl_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_009")
                .put("cardType", "image_text_list")
                .put("title", "推荐")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("title", "No image")
                                .put("subtitle", "desc")))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("imageUrl")));
    }

    @Test
    public void validate_missingRequestId_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("cardType", "text_summary")
                .put("title", "Test")
                .put("content", "Hello")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("requestId")));
    }

    @Test
    public void validate_missingTitle_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_010")
                .put("cardType", "text_summary")
                .put("content", "Hello")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("title")));
    }

    @Test
    public void validate_emptyJson_returnsInvalid() {
        CardContractValidator.ValidationResult result = CardContractValidator.validate("");
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("empty")));
    }

    @Test
    public void validate_invalidJson_returnsInvalid() {
        CardContractValidator.ValidationResult result = CardContractValidator.validate("not json");
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Invalid JSON")));
    }

    @Test
    public void validate_nullInput_returnsInvalid() {
        CardContractValidator.ValidationResult result = CardContractValidator.validate(null);
        assertFalse(result.isValid());
    }

    @Test
    public void validate_validSportsScoreList_returnsValid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "nba_001")
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
                                .put("awayTeam", new JSONObject().put("name", "76人")))
                        .put(new JSONObject()
                                .put("status", "live")
                                .put("homeTeam", new JSONObject().put("name", "勇士").put("score", 85))
                                .put("awayTeam", new JSONObject().put("name", "掘金").put("score", 78))))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void validate_sportsScoreListEmptyItems_returnsValid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "nba_002")
                .put("cardType", "sports_score_list")
                .put("title", "NBA 今日赛况")
                .put("items", new JSONArray())
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void validate_sportsScoreListMissingHomeTeamName_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "nba_003")
                .put("cardType", "sports_score_list")
                .put("title", "NBA")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("status", "final")
                                .put("homeTeam", new JSONObject().put("name", ""))
                                .put("awayTeam", new JSONObject().put("name", "凯尔特人"))))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("homeTeam.name")));
    }

    @Test
    public void validate_sportsScoreListMissingAwayTeamName_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "nba_004")
                .put("cardType", "sports_score_list")
                .put("title", "NBA")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("status", "final")
                                .put("homeTeam", new JSONObject().put("name", "湖人"))
                                .put("awayTeam", new JSONObject())))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("awayTeam")));
    }

    @Test
    public void validate_sportsScoreListUnknownStatus_returnsWarningButValid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "nba_005")
                .put("cardType", "sports_score_list")
                .put("title", "NBA")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("status", "postponed")
                                .put("homeTeam", new JSONObject().put("name", "湖人"))
                                .put("awayTeam", new JSONObject().put("name", "凯尔特人"))))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("unknown status")));
    }

    @Test
    public void validate_sportsScoreListFinalMissingScore_returnsWarningButValid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "nba_006")
                .put("cardType", "sports_score_list")
                .put("title", "NBA")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("status", "final")
                                .put("homeTeam", new JSONObject().put("name", "勇士"))
                                .put("awayTeam", new JSONObject().put("name", "掘金"))))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("score")));
    }

    @Test
    public void validate_sportsScoreListOverMaxItems_returnsWarningButValid() throws Exception {
        JSONArray items = new JSONArray();
        for (int i = 0; i < 25; i++) {
            items.put(new JSONObject()
                    .put("status", "scheduled")
                    .put("homeTeam", new JSONObject().put("name", "TeamA" + i))
                    .put("awayTeam", new JSONObject().put("name", "TeamB" + i)));
        }
        String json = new JSONObject()
                .put("requestId", "nba_007")
                .put("cardType", "sports_score_list")
                .put("title", "NBA")
                .put("items", items)
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("exceeds")));
    }

    @Test
    public void validate_sportsScoreListNullScore_returnsWarningButValid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "nba_null_score")
                .put("cardType", "sports_score_list")
                .put("title", "NBA")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("status", "final")
                                .put("homeTeam", new JSONObject().put("name", "湖人").put("score", JSONObject.NULL))
                                .put("awayTeam", new JSONObject().put("name", "凯尔特人").put("score", ""))))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        // Both null and empty string score should produce warnings
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("homeTeam.score")));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("awayTeam.score")));
    }

    @Test
    public void validate_validWeatherSummary_returnsValid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "weather_001")
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

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    public void validate_weatherSummaryMissingCurrent_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "weather_002")
                .put("cardType", "weather_summary")
                .put("title", "天气")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("current")));
    }

    @Test
    public void validate_weatherSummaryMissingLocation_returnsWarning() throws Exception {
        String json = new JSONObject()
                .put("requestId", "weather_003")
                .put("cardType", "weather_summary")
                .put("title", "天气")
                .put("current", new JSONObject()
                        .put("condition", "晴")
                        .put("temperature", 26))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("location")));
    }

    @Test
    public void validate_weatherSummaryLocationInCurrentNotTopLevel_stillWarns() throws Exception {
        // location is a top-level field; putting it inside current should NOT satisfy the warning
        String json = new JSONObject()
                .put("requestId", "weather_loc_level")
                .put("cardType", "weather_summary")
                .put("title", "天气")
                .put("current", new JSONObject()
                        .put("condition", "晴")
                        .put("temperature", 26)
                        .put("location", "上海"))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
        // Still warns because location is checked at top level, not inside current
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("location")));
    }

    @Test
    public void validate_weatherSummaryMissingTemperature_returnsWarning() throws Exception {
        String json = new JSONObject()
                .put("requestId", "weather_004")
                .put("cardType", "weather_summary")
                .put("title", "天气")
                .put("location", "上海")
                .put("current", new JSONObject()
                        .put("condition", "晴"))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("temperature")));
    }

    @Test
    public void validate_weatherSummaryMissingCondition_returnsWarning() throws Exception {
        String json = new JSONObject()
                .put("requestId", "weather_005")
                .put("cardType", "weather_summary")
                .put("title", "天气")
                .put("current", new JSONObject()
                        .put("temperature", 26))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("condition")));
    }

    @Test
    public void validate_weatherSummaryTipsNotArray_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "weather_006")
                .put("cardType", "weather_summary")
                .put("title", "天气")
                .put("current", new JSONObject().put("condition", "晴"))
                .put("tips", "not an array")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("tips")));
    }

    @Test
    public void validate_weatherSummaryTipsOverMax_returnsWarning() throws Exception {
        JSONArray tips = new JSONArray();
        for (int i = 0; i < 6; i++) {
            tips.put("tip_" + i);
        }
        String json = new JSONObject()
                .put("requestId", "weather_007")
                .put("cardType", "weather_summary")
                .put("title", "天气")
                .put("location", "上海")
                .put("current", new JSONObject()
                        .put("condition", "晴")
                        .put("temperature", 26))
                .put("tips", tips)
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("exceeds")));
    }

    @Test
    public void validate_weatherSummaryMinimalCurrent_returnsValidWithWarnings() throws Exception {
        String json = new JSONObject()
                .put("requestId", "weather_008")
                .put("cardType", "weather_summary")
                .put("title", "天气")
                .put("current", new JSONObject())
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
        assertEquals(3, result.getWarnings().size());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("location")));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("temperature")));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("condition")));
    }
}