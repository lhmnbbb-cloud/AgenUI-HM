package com.amap.agenuidemo.card.template;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

import com.amap.agenuidemo.A2uiJsonValidator;

public class WeatherSummaryTemplateTest {

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
                .put("requestId", "weather_basic")
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
                        .put("早晚温差较大，注意添衣"));

        String[] messages = WeatherSummaryTemplate.render(data);
        assertEquals(3, messages.length);
        assertEquals("{}", messages[2]);

        JSONObject create = new JSONObject(messages[0]);
        assertEquals("card_weather_basic", create.getJSONObject("createSurface").getString("surfaceId"));

        JSONObject update = new JSONObject(messages[1]);
        JSONArray components = update.getJSONObject("updateComponents").getJSONArray("components");
        // Full data (2 tips): root + title + location-time + card + content + primary-row + condition + temperature
        // + highlow-row + high + low + detail-row + airquality + humidity + wind + tips-col + tip0 + tip1 = 18
        assertEquals(18, components.length());

        assertPassesA2uiValidator(messages);
    }

    @Test
    public void render_basic_containsAllSections() throws Exception {
        JSONObject data = new JSONObject()
                .put("requestId", "weather_sections")
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
                        .put("早晚温差较大，注意添衣"));

        String[] messages = WeatherSummaryTemplate.render(data);
        JSONArray components = new JSONObject(messages[1])
                .getJSONObject("updateComponents").getJSONArray("components");

        JSONObject condition = findComponentById(components, "condition-text");
        assertNotNull(condition);
        assertEquals("晴转多云", condition.getString("text"));

        JSONObject temperature = findComponentById(components, "temperature-text");
        assertNotNull(temperature);
        assertEquals("26°", temperature.getString("text"));

        JSONObject high = findComponentById(components, "high-text");
        assertNotNull(high);
        assertEquals("最高 30°", high.getString("text"));

        JSONObject low = findComponentById(components, "low-text");
        assertNotNull(low);
        assertEquals("最低 18°", low.getString("text"));

        JSONObject airQuality = findComponentById(components, "airquality-text");
        assertNotNull(airQuality);
        assertEquals("空气质量: 良", airQuality.getString("text"));

        JSONObject humidity = findComponentById(components, "humidity-text");
        assertNotNull(humidity);
        assertEquals("湿度: 65%", humidity.getString("text"));

        JSONObject wind = findComponentById(components, "wind-text");
        assertNotNull(wind);
        assertEquals("风力: 东南风 3级", wind.getString("text"));

        JSONObject tip0 = findComponentById(components, "tips-item_0");
        assertNotNull(tip0);
        assertEquals("• 紫外线较强，注意防晒", tip0.getString("text"));

        JSONObject tip1 = findComponentById(components, "tips-item_1");
        assertNotNull(tip1);
        assertEquals("• 早晚温差较大，注意添衣", tip1.getString("text"));
    }

    @Test
    public void render_partialData_rendersGracefully() throws Exception {
        JSONObject data = new JSONObject()
                .put("requestId", "weather_partial")
                .put("title", "天气")
                .put("current", new JSONObject()
                        .put("condition", "晴")
                        .put("temperature", 28));

        String[] messages = WeatherSummaryTemplate.render(data);
        JSONArray components = new JSONObject(messages[1])
                .getJSONObject("updateComponents").getJSONArray("components");

        // root + title + card + content + primary-row + condition + temperature = 7
        assertEquals(7, components.length());

        assertNull(findComponentById(components, "location-time-text"));
        assertNull(findComponentById(components, "highlow-row"));
        assertNull(findComponentById(components, "detail-row"));
        assertNull(findComponentById(components, "tips-col"));

        assertPassesA2uiValidator(messages);
    }

    @Test
    public void render_noLocationNoTime_skipsLocationText() throws Exception {
        JSONObject data = new JSONObject()
                .put("requestId", "weather_noloc")
                .put("title", "天气")
                .put("current", new JSONObject()
                        .put("condition", "晴")
                        .put("temperature", 28));

        String[] messages = WeatherSummaryTemplate.render(data);
        JSONArray components = new JSONObject(messages[1])
                .getJSONObject("updateComponents").getJSONArray("components");

        assertNull(findComponentById(components, "location-time-text"));
        assertPassesA2uiValidator(messages);
    }

    @Test
    public void formatTemperature_number_returnsWithDegree() throws Exception {
        JSONObject current = new JSONObject().put("temperature", 26);
        assertEquals("26°", WeatherSummaryTemplate.formatTemperature(current));
    }

    @Test
    public void formatTemperature_string_returnsWithDegree() throws Exception {
        JSONObject current = new JSONObject().put("temperature", "26");
        assertEquals("26°", WeatherSummaryTemplate.formatTemperature(current));
    }

    @Test
    public void formatTemperature_stringAlreadyWithDegree_returnsAsIs() throws Exception {
        JSONObject current = new JSONObject().put("temperature", "26°");
        assertEquals("26°", WeatherSummaryTemplate.formatTemperature(current));
    }

    @Test
    public void formatTemperature_null_returnsEmpty() throws Exception {
        JSONObject current = new JSONObject().put("temperature", JSONObject.NULL);
        assertEquals("", WeatherSummaryTemplate.formatTemperature(current));

        JSONObject noTemp = new JSONObject();
        assertEquals("", WeatherSummaryTemplate.formatTemperature(noTemp));

        assertEquals("", WeatherSummaryTemplate.formatTemperature(null));
    }

    @Test
    public void formatTemperature_emptyString_returnsEmpty() throws Exception {
        JSONObject current = new JSONObject().put("temperature", "");
        assertEquals("", WeatherSummaryTemplate.formatTemperature(current));
    }

    @Test
    public void formatDegreeField_number_returnsWithDegree() throws Exception {
        JSONObject current = new JSONObject().put("high", 30);
        assertEquals("30°", WeatherSummaryTemplate.formatDegreeField(current, "high"));
    }

    @Test
    public void formatDegreeField_string_returnsWithDegree() throws Exception {
        JSONObject current = new JSONObject().put("low", "18");
        assertEquals("18°", WeatherSummaryTemplate.formatDegreeField(current, "low"));
    }

    @Test
    public void formatDegreeField_stringAlreadyWithDegree_returnsAsIs() throws Exception {
        JSONObject current = new JSONObject().put("high", "30°");
        assertEquals("30°", WeatherSummaryTemplate.formatDegreeField(current, "high"));
    }

    @Test
    public void formatDegreeField_missing_returnsEmpty() throws Exception {
        JSONObject current = new JSONObject();
        assertEquals("", WeatherSummaryTemplate.formatDegreeField(current, "high"));
        assertEquals("", WeatherSummaryTemplate.formatDegreeField(null, "low"));
    }

    @Test
    public void render_minimalCurrent_showsPlaceholder() throws Exception {
        JSONObject data = new JSONObject()
                .put("requestId", "weather_minimal")
                .put("cardType", "weather_summary")
                .put("title", "天气")
                .put("current", new JSONObject());

        String[] messages = WeatherSummaryTemplate.render(data);
        JSONArray components = new JSONObject(messages[1])
                .getJSONObject("updateComponents").getJSONArray("components");

        // root + title + card + content + primary-row + condition-text(placeholder) = 6
        assertEquals(6, components.length());

        JSONObject condition = findComponentById(components, "condition-text");
        assertNotNull(condition);
        assertEquals("暂无天气数据", condition.getString("text"));

        // No temperature-text when both condition and temperature are empty
        assertNull(findComponentById(components, "temperature-text"));

        assertPassesA2uiValidator(messages);
    }

    @Test
    public void formatLocationTime_both_returnsCombined() throws Exception {
        assertEquals("上海 · 14:30 更新", WeatherSummaryTemplate.formatLocationTime("上海", "14:30 更新"));
    }

    @Test
    public void formatLocationTime_onlyLocation_returnsLocation() throws Exception {
        assertEquals("上海", WeatherSummaryTemplate.formatLocationTime("上海", ""));
    }

    @Test
    public void formatLocationTime_onlyTime_returnsTime() throws Exception {
        assertEquals("14:30 更新", WeatherSummaryTemplate.formatLocationTime("", "14:30 更新"));
    }

    @Test
    public void formatLocationTime_bothEmpty_returnsEmpty() throws Exception {
        assertEquals("", WeatherSummaryTemplate.formatLocationTime("", ""));
    }

    @Test
    public void render_basic_textsUseMluiVariants() throws Exception {
        JSONObject data = new JSONObject()
                .put("requestId", "weather_variants")
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
                        .put("早晚温差较大，注意添衣"));

        String[] messages = WeatherSummaryTemplate.render(data);
        JSONArray components = new JSONObject(messages[1])
                .getJSONObject("updateComponents").getJSONArray("components");

        assertEquals(WeatherSummaryTemplate.VARIANT_TITLE,
                findComponentById(components, "title-text").getString("variant"));
        assertEquals(WeatherSummaryTemplate.VARIANT_LABEL,
                findComponentById(components, "location-time-text").getString("variant"));
        assertEquals(WeatherSummaryTemplate.VARIANT_BODY,
                findComponentById(components, "condition-text").getString("variant"));
        assertEquals(WeatherSummaryTemplate.VARIANT_TITLE_LARGE,
                findComponentById(components, "temperature-text").getString("variant"));
        assertEquals(WeatherSummaryTemplate.VARIANT_CONTENT,
                findComponentById(components, "high-text").getString("variant"));
        assertEquals(WeatherSummaryTemplate.VARIANT_CONTENT,
                findComponentById(components, "low-text").getString("variant"));
        assertEquals(WeatherSummaryTemplate.VARIANT_CONTENT,
                findComponentById(components, "airquality-text").getString("variant"));
        assertEquals(WeatherSummaryTemplate.VARIANT_CONTENT,
                findComponentById(components, "humidity-text").getString("variant"));
        assertEquals(WeatherSummaryTemplate.VARIANT_CONTENT,
                findComponentById(components, "wind-text").getString("variant"));
        assertEquals(WeatherSummaryTemplate.VARIANT_LABEL,
                findComponentById(components, "tips-item_0").getString("variant"));
        assertEquals(WeatherSummaryTemplate.VARIANT_LABEL,
                findComponentById(components, "tips-item_1").getString("variant"));
    }

    @Test
    public void render_basic_textStylesOmitFontSizeColorWeight() throws Exception {
        JSONObject data = new JSONObject()
                .put("requestId", "weather_no_hardcoded_styles")
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
                        .put("紫外线较强，注意防晒"));

        String[] messages = WeatherSummaryTemplate.render(data);
        JSONArray components = new JSONObject(messages[1])
                .getJSONObject("updateComponents").getJSONArray("components");

        String[] textIds = {
                "title-text", "location-time-text", "condition-text", "temperature-text",
                "high-text", "low-text", "airquality-text", "humidity-text", "wind-text",
                "tips-item_0"
        };
        for (String id : textIds) {
            JSONObject comp = findComponentById(components, id);
            assertNotNull("Missing text component: " + id, comp);
            JSONObject styles = comp.optJSONObject("styles");
            if (styles == null) continue;
            assertFalse(id + " should not hardcode font-size", styles.has("font-size"));
            assertFalse(id + " should not hardcode color", styles.has("color"));
            assertFalse(id + " should not hardcode font-weight", styles.has("font-weight"));
            assertFalse(id + " should not hardcode line-height", styles.has("line-height"));
        }
    }

    @Test
    public void render_minimalPlaceholder_usesMluiLabelVariant() throws Exception {
        JSONObject data = new JSONObject()
                .put("requestId", "weather_minimal_variant")
                .put("title", "天气")
                .put("current", new JSONObject());

        String[] messages = WeatherSummaryTemplate.render(data);
        JSONArray components = new JSONObject(messages[1])
                .getJSONObject("updateComponents").getJSONArray("components");

        JSONObject placeholder = findComponentById(components, "condition-text");
        assertNotNull(placeholder);
        assertEquals(WeatherSummaryTemplate.VARIANT_LABEL, placeholder.getString("variant"));
        JSONObject styles = placeholder.optJSONObject("styles");
        if (styles != null) {
            assertFalse(styles.has("font-size"));
            assertFalse(styles.has("color"));
            assertFalse(styles.has("font-weight"));
        }
    }

    @Test
    public void render_weatherCard_usesMluiCardPrimaryVariant() throws Exception {
        JSONObject data = new JSONObject()
                .put("requestId", "weather_card_variant")
                .put("title", "天气")
                .put("current", new JSONObject()
                        .put("condition", "晴")
                        .put("temperature", 28));

        String[] messages = WeatherSummaryTemplate.render(data);
        JSONArray components = new JSONObject(messages[1])
                .getJSONObject("updateComponents").getJSONArray("components");

        JSONObject card = findComponentById(components, "weather-card");
        assertNotNull(card);
        assertEquals("mluiCardPrimary", card.getString("variant"));
    }

    @Test
    public void render_weatherCard_stylesOmitBackgroundAndBorderRadius() throws Exception {
        JSONObject data = new JSONObject()
                .put("requestId", "weather_card_no_bg")
                .put("title", "天气")
                .put("current", new JSONObject()
                        .put("condition", "晴")
                        .put("temperature", 28));

        String[] messages = WeatherSummaryTemplate.render(data);
        JSONArray components = new JSONObject(messages[1])
                .getJSONObject("updateComponents").getJSONArray("components");

        JSONObject card = findComponentById(components, "weather-card");
        assertNotNull(card);
        JSONObject styles = card.optJSONObject("styles");
        assertNotNull(styles);
        assertFalse("weather-card should not hardcode background-color",
                styles.has("background-color"));
        assertFalse("weather-card should not hardcode border-radius",
                styles.has("border-radius"));
        assertTrue("weather-card should still have padding",
                styles.has("padding"));
    }

    @Test
    public void render_weatherCard_withVariant_stillPassesA2uiValidator() throws Exception {
        JSONObject data = new JSONObject()
                .put("requestId", "weather_card_valid")
                .put("title", "天气")
                .put("location", "上海")
                .put("updatedAt", "14:30")
                .put("current", new JSONObject()
                        .put("condition", "晴")
                        .put("temperature", 28)
                        .put("high", 30)
                        .put("low", 18));

        String[] messages = WeatherSummaryTemplate.render(data);
        assertPassesA2uiValidator(messages);
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