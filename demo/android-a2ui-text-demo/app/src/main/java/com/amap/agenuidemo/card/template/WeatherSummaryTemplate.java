package com.amap.agenuidemo.card.template;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Template that converts weather_summary CardData to A2UI v0.9 messages.
 *
 * Delegated from CardTemplateRenderer for WEATHER_SUMMARY card type.
 * Output is always 3 messages: [createSurface, updateComponents, "{}"].
 *
 * <p>Text components use the demo-side semantic variants registered by
 * {@code assets/common_controls_theme.json} (mluiTitle / mluiTitleLarge /
 * mluiBody / mluiContent / mluiLabel). The C++ spec engine expands each
 * variant to a {@code melo-text-appearance} style key which MeloTextComponent
 * applies via {@code setTextAppearance}. Standard Java components ignore the
 * key, so the template still renders sensibly when the proprietary AAR is
 * absent.
 */
public class WeatherSummaryTemplate {

    private static final String VERSION = "v0.9";
    private static final String CATALOG_ID = "https://a2ui.org/specification/v0_9/standard_catalog.json";

    // Demo-side semantic variants (see assets/common_controls_theme.json).
    static final String VARIANT_TITLE = "mluiTitle";
    static final String VARIANT_TITLE_LARGE = "mluiTitleLarge";
    static final String VARIANT_BODY = "mluiBody";
    static final String VARIANT_CONTENT = "mluiContent";
    static final String VARIANT_LABEL = "mluiLabel";

    public static String[] render(JSONObject data) throws Exception {
        String surfaceId = "card_" + data.optString("requestId", "unknown");
        String title = data.getString("title");
        JSONObject current = data.optJSONObject("current");

        String location = data.optString("location", "");
        String updatedAt = data.optString("updatedAt", "");
        String locationTime = formatLocationTime(location, updatedAt);

        JSONArray componentArray = new JSONArray();
        JSONArray rootChildren = new JSONArray();
        rootChildren.put("title-text");

        // Root Column
        componentArray.put(new JSONObject()
                .put("id", "root")
                .put("component", "Column")
                .put("children", rootChildren)
                .put("align", "stretch")
                .put("styles", new JSONObject()
                        .put("padding", "20px")
                        .put("gap", "16px")
                        .put("background-color", "#F5F5F5")));

        // Title — semantic variant; weight/size come from MluiTextAppearance.Title
        componentArray.put(new JSONObject()
                .put("id", "title-text")
                .put("component", "Text")
                .put("text", title)
                .put("variant", VARIANT_TITLE)
                .put("styles", new JSONObject()
                        .put("text-align", "left")));

        // Location + time line (optional, auxiliary label)
        if (!locationTime.isEmpty()) {
            rootChildren.put("location-time-text");
            componentArray.put(new JSONObject()
                    .put("id", "location-time-text")
                    .put("component", "Text")
                    .put("text", locationTime)
                    .put("variant", VARIANT_LABEL)
                    .put("styles", new JSONObject()
                            .put("text-align", "left")));
        }

        // Weather card
        rootChildren.put("weather-card");
        JSONArray contentChildren = new JSONArray();
        contentChildren.put("primary-row");

        componentArray.put(new JSONObject()
                .put("id", "weather-card")
                .put("component", "Card")
                .put("variant", "mluiCardPrimary")
                .put("child", "weather-content")
                .put("styles", new JSONObject()
                        .put("width", "auto")
                        .put("height", "auto")
                        .put("padding", "24px")));

        // Primary row: condition + temperature (or placeholder when both empty)
        String condition = current != null ? current.optString("condition", "") : "";
        String temperature = current != null ? formatTemperature(current) : "";

        if (condition.isEmpty() && temperature.isEmpty()) {
            // Both empty → show placeholder instead of blank Text components
            componentArray.put(new JSONObject()
                    .put("id", "primary-row")
                    .put("component", "Row")
                    .put("children", new JSONArray().put("condition-text"))
                    .put("styles", new JSONObject().put("gap", "8px")));

            componentArray.put(new JSONObject()
                    .put("id", "condition-text")
                    .put("component", "Text")
                    .put("text", "暂无天气数据")
                    .put("variant", VARIANT_LABEL)
                    .put("styles", new JSONObject()
                            .put("text-align", "left")));
        } else {
            componentArray.put(new JSONObject()
                    .put("id", "primary-row")
                    .put("component", "Row")
                    .put("children", new JSONArray().put("condition-text").put("temperature-text"))
                    .put("styles", new JSONObject().put("gap", "8px")));

            componentArray.put(new JSONObject()
                    .put("id", "condition-text")
                    .put("component", "Text")
                    .put("text", condition)
                    .put("variant", VARIANT_BODY)
                    .put("styles", new JSONObject().put("text-align", "left")));

            // Big temperature number — uses MluiTextAppearance.Title.Large
            componentArray.put(new JSONObject()
                    .put("id", "temperature-text")
                    .put("component", "Text")
                    .put("text", temperature)
                    .put("variant", VARIANT_TITLE_LARGE)
                    .put("styles", new JSONObject().put("text-align", "left")));
        }

        // High/Low row (optional)
        String high = current != null ? formatDegreeField(current, "high") : "";
        String low = current != null ? formatDegreeField(current, "low") : "";

        if (!high.isEmpty() || !low.isEmpty()) {
            contentChildren.put("highlow-row");
            JSONArray highlowChildren = new JSONArray();
            if (!high.isEmpty()) highlowChildren.put("high-text");
            if (!low.isEmpty()) highlowChildren.put("low-text");

            componentArray.put(new JSONObject()
                    .put("id", "highlow-row")
                    .put("component", "Row")
                    .put("children", highlowChildren)
                    .put("styles", new JSONObject().put("gap", "16px")));

            if (!high.isEmpty()) {
                componentArray.put(new JSONObject()
                        .put("id", "high-text")
                        .put("component", "Text")
                        .put("text", "最高 " + high)
                        .put("variant", VARIANT_CONTENT)
                        .put("styles", new JSONObject().put("text-align", "left")));
            }
            if (!low.isEmpty()) {
                componentArray.put(new JSONObject()
                        .put("id", "low-text")
                        .put("component", "Text")
                        .put("text", "最低 " + low)
                        .put("variant", VARIANT_CONTENT)
                        .put("styles", new JSONObject().put("text-align", "left")));
            }
        }

        // Detail row (optional: airQuality/humidity/wind)
        String airQuality = current != null ? current.optString("airQuality", "") : "";
        String humidity = current != null ? current.optString("humidity", "") : "";
        String wind = current != null ? current.optString("wind", "") : "";

        if (!airQuality.isEmpty() || !humidity.isEmpty() || !wind.isEmpty()) {
            contentChildren.put("detail-row");
            JSONArray detailChildren = new JSONArray();
            if (!airQuality.isEmpty()) detailChildren.put("airquality-text");
            if (!humidity.isEmpty()) detailChildren.put("humidity-text");
            if (!wind.isEmpty()) detailChildren.put("wind-text");

            componentArray.put(new JSONObject()
                    .put("id", "detail-row")
                    .put("component", "Row")
                    .put("children", detailChildren)
                    .put("styles", new JSONObject().put("gap", "16px")));

            if (!airQuality.isEmpty()) {
                componentArray.put(new JSONObject()
                        .put("id", "airquality-text")
                        .put("component", "Text")
                        .put("text", "空气质量: " + airQuality)
                        .put("variant", VARIANT_CONTENT)
                        .put("styles", new JSONObject().put("text-align", "left")));
            }
            if (!humidity.isEmpty()) {
                componentArray.put(new JSONObject()
                        .put("id", "humidity-text")
                        .put("component", "Text")
                        .put("text", "湿度: " + humidity)
                        .put("variant", VARIANT_CONTENT)
                        .put("styles", new JSONObject().put("text-align", "left")));
            }
            if (!wind.isEmpty()) {
                componentArray.put(new JSONObject()
                        .put("id", "wind-text")
                        .put("component", "Text")
                        .put("text", "风力: " + wind)
                        .put("variant", VARIANT_CONTENT)
                        .put("styles", new JSONObject().put("text-align", "left")));
            }
        }

        // Tips column (optional)
        JSONArray tips = data.optJSONArray("tips");
        if (tips != null && tips.length() > 0) {
            contentChildren.put("tips-col");
            JSONArray tipsChildren = new JSONArray();
            for (int i = 0; i < tips.length(); i++) {
                tipsChildren.put("tips-item_" + i);
            }

            componentArray.put(new JSONObject()
                    .put("id", "tips-col")
                    .put("component", "Column")
                    .put("children", tipsChildren)
                    .put("align", "stretch")
                    .put("styles", new JSONObject().put("gap", "4px")));

            for (int i = 0; i < tips.length(); i++) {
                String tipText = tips.optString(i, "");
                componentArray.put(new JSONObject()
                        .put("id", "tips-item_" + i)
                        .put("component", "Text")
                        .put("text", "• " + tipText)
                        .put("variant", VARIANT_LABEL)
                        .put("styles", new JSONObject().put("text-align", "left")));
            }
        }

        // Weather content column
        componentArray.put(new JSONObject()
                .put("id", "weather-content")
                .put("component", "Column")
                .put("children", contentChildren)
                .put("align", "stretch")
                .put("styles", new JSONObject().put("gap", "12px")));

        JSONObject createSurface = buildCreateSurface(surfaceId);
        JSONObject updateComponents = new JSONObject()
                .put("version", VERSION)
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", surfaceId)
                        .put("components", componentArray));

        return new String[]{createSurface.toString(), updateComponents.toString(), "{}"};
    }

    static String formatTemperature(JSONObject current) {
        if (current == null || !current.has("temperature") || current.isNull("temperature")) {
            return "";
        }
        try {
            Object tempObj = current.get("temperature");
            if (tempObj instanceof Number) {
                return ((Number) tempObj).intValue() + "°";
            }
            return normalizeDegreeSuffix(tempObj.toString());
        } catch (Exception e) {
            return "";
        }
    }

    static String formatDegreeField(JSONObject current, String field) {
        if (current == null || !current.has(field) || current.isNull(field)) {
            return "";
        }
        try {
            Object val = current.get(field);
            if (val instanceof Number) {
                return ((Number) val).intValue() + "°";
            }
            String strVal = val.toString();
            if (strVal.isEmpty()) {
                return "";
            }
            return normalizeDegreeSuffix(strVal);
        } catch (Exception e) {
            return "";
        }
    }

    private static String normalizeDegreeSuffix(String value) {
        if (value.isEmpty()) return "";
        if (value.endsWith("°")) return value;
        return value + "°";
    }

    static String formatLocationTime(String location, String updatedAt) {
        boolean hasLocation = location != null && !location.isEmpty();
        boolean hasUpdatedAt = updatedAt != null && !updatedAt.isEmpty();
        if (hasLocation && hasUpdatedAt) {
            return location + " · " + updatedAt;
        }
        if (hasLocation) {
            return location;
        }
        if (hasUpdatedAt) {
            return updatedAt;
        }
        return "";
    }

    private static JSONObject buildCreateSurface(String surfaceId) throws Exception {
        return new JSONObject()
                .put("version", VERSION)
                .put("createSurface", new JSONObject()
                        .put("surfaceId", surfaceId)
                        .put("catalogId", CATALOG_ID));
    }
}
