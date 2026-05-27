package com.amap.agenuidemo.card;

import com.amap.agenuidemo.card.template.SportsScoreListTemplate;
import com.amap.agenuidemo.card.template.WeatherSummaryTemplate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministically converts validated card data to A2UI v0.9 protocol messages.
 *
 * No LLM involvement. No A2uiMessageNormalizer dependency.
 * Each CardType maps to a fixed template that always produces valid A2UI
 * (passing A2uiJsonValidator) from valid card data.
 */
public class CardTemplateRenderer {

    private static final String VERSION = "v0.9";
    private static final String CATALOG_ID = "https://a2ui.org/specification/v0_9/standard_catalog.json";

    public static CardRenderResult render(String cardDataJson) {
        CardContractValidator.ValidationResult validation = CardContractValidator.validate(cardDataJson);
        if (!validation.isValid()) {
            return renderFallback(cardDataJson, validation.getErrors());
        }

        JSONObject data;
        try {
            data = new JSONObject(cardDataJson);
        } catch (Exception e) {
            return renderFallback(cardDataJson, List.of("JSON parse failed: " + e.getMessage()));
        }

        CardType cardType = CardType.fromKey(data.optString("cardType", ""));
        if (cardType == null) {
            return renderFallback(cardDataJson, List.of("Unknown cardType"));
        }

        try {
            String[] messages;
            switch (cardType) {
                case TEXT_SUMMARY:
                    messages = renderTextSummary(data);
                    break;
                case TEXT_LIST:
                    messages = renderTextList(data);
                    break;
                case IMAGE_TEXT_LIST:
                    messages = renderImageTextList(data);
                    break;
                case SPORTS_SCORE_LIST:
                    messages = SportsScoreListTemplate.render(data);
                    break;
                case WEATHER_SUMMARY:
                    messages = WeatherSummaryTemplate.render(data);
                    break;
                default:
                    return renderFallback(cardDataJson, List.of("Unsupported cardType: " + cardType.getKey()));
            }
            return new CardRenderResult(true, messages, new ArrayList<>(), validation.getWarnings());
        } catch (Exception e) {
            return renderFallback(cardDataJson, List.of("Render failed: " + e.getMessage()));
        }
    }

    private static String[] renderTextSummary(JSONObject data) throws Exception {
        String surfaceId = "card_" + data.optString("requestId", "unknown");
        String title = data.getString("title");
        String content = data.getString("content");

        JSONObject createSurface = buildCreateSurface(surfaceId);
        JSONObject updateComponents = new JSONObject()
                .put("version", VERSION)
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", surfaceId)
                        .put("components", new JSONArray()
                                .put(new JSONObject()
                                        .put("id", "root")
                                        .put("component", "Column")
                                        .put("children", new JSONArray().put("card"))
                                        .put("align", "stretch")
                                        .put("styles", new JSONObject()
                                                .put("padding", "20px")
                                                .put("gap", "16px")
                                                .put("background-color", "#F5F5F5")))
                                .put(new JSONObject()
                                        .put("id", "card")
                                        .put("component", "Card")
                                        .put("child", "card-content")
                                        .put("styles", new JSONObject()
                                                .put("width", "auto")
                                                .put("height", "auto")
                                                .put("padding", "24px")
                                                .put("border-radius", "16px")
                                                .put("background-color", "#FFFFFF")
                                                .put("filter", "drop-shadow(0px 6px 24px 0px rgba(0, 0, 0, 0.08))")))
                                .put(new JSONObject()
                                        .put("id", "card-content")
                                        .put("component", "Column")
                                        .put("children", new JSONArray().put("title-text").put("content-text"))
                                        .put("align", "stretch"))
                                .put(new JSONObject()
                                        .put("id", "title-text")
                                        .put("component", "Text")
                                        .put("text", title)
                                        .put("variant", "h2")
                                        .put("styles", new JSONObject()
                                                .put("text-align", "left")
                                                .put("font-weight", "bold")))
                                .put(new JSONObject()
                                        .put("id", "content-text")
                                        .put("component", "Text")
                                        .put("text", content)
                                        .put("variant", "body")
                                        .put("styles", new JSONObject()
                                                .put("text-align", "left")
                                                .put("color", "#000000E6")))));

        return new String[]{createSurface.toString(), updateComponents.toString(), "{}"};
    }

    private static String[] renderTextList(JSONObject data) throws Exception {
        String surfaceId = "card_" + data.optString("requestId", "unknown");
        String title = data.getString("title");
        JSONArray items = data.getJSONArray("items");

        JSONArray componentArray = new JSONArray();
        JSONArray listChildIds = new JSONArray();

        // Root Column with title + list
        JSONArray rootChildren = new JSONArray().put("title-text").put("item-list");
        componentArray.put(new JSONObject()
                .put("id", "root")
                .put("component", "Column")
                .put("children", rootChildren)
                .put("align", "stretch")
                .put("styles", new JSONObject()
                        .put("padding", "20px")
                        .put("gap", "16px")
                        .put("background-color", "#F5F5F5")));
        componentArray.put(new JSONObject()
                .put("id", "title-text")
                .put("component", "Text")
                .put("text", title)
                .put("variant", "h2")
                .put("styles", new JSONObject().put("text-align", "left")));

        // List component
        componentArray.put(new JSONObject()
                .put("id", "item-list")
                .put("component", "List")
                .put("children", listChildIds)
                .put("align", "stretch"));

        // Item text components
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String itemId = "item_" + i;
            String itemText = item.optString("text", "");
            listChildIds.put(itemId);
            componentArray.put(new JSONObject()
                    .put("id", itemId)
                    .put("component", "Text")
                    .put("text", itemText)
                    .put("variant", "body")
                    .put("styles", new JSONObject().put("padding", "12px 16px")));
        }

        JSONObject createSurface = buildCreateSurface(surfaceId);
        JSONObject updateComponents = new JSONObject()
                .put("version", VERSION)
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", surfaceId)
                        .put("components", componentArray));

        return new String[]{createSurface.toString(), updateComponents.toString(), "{}"};
    }

    private static String[] renderImageTextList(JSONObject data) throws Exception {
        String surfaceId = "card_" + data.optString("requestId", "unknown");
        String title = data.getString("title");
        JSONArray items = data.getJSONArray("items");

        JSONArray componentArray = new JSONArray();
        JSONArray rootChildren = new JSONArray().put("title-text");

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
        componentArray.put(new JSONObject()
                .put("id", "title-text")
                .put("component", "Text")
                .put("text", title)
                .put("variant", "h2")
                .put("styles", new JSONObject().put("text-align", "left")));

        // Each item as a Card with Row containing Image + Column(text)
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String cardId = "img_card_" + i;
            String rowId = "img_row_" + i;
            String imgId = "img_img_" + i;
            String textColId = "img_text_col_" + i;
            String itemTitleId = "img_title_" + i;
            String itemSubId = "img_sub_" + i;

            rootChildren.put(cardId);

            // Card
            componentArray.put(new JSONObject()
                    .put("id", cardId)
                    .put("component", "Card")
                    .put("child", rowId)
                    .put("styles", new JSONObject()
                            .put("width", "auto")
                            .put("height", "auto")
                            .put("padding", "16px")
                            .put("border-radius", "12px")
                            .put("background-color", "#FFFFFF")));

            // Row: Image + Text Column
            componentArray.put(new JSONObject()
                    .put("id", rowId)
                    .put("component", "Row")
                    .put("children", new JSONArray().put(imgId).put(textColId))
                    .put("styles", new JSONObject().put("gap", "12px")));

            // Image
            componentArray.put(new JSONObject()
                    .put("id", imgId)
                    .put("component", "Image")
                    .put("src", item.optString("imageUrl", ""))
                    .put("styles", new JSONObject()
                            .put("width", "80px")
                            .put("height", "80px")
                            .put("border-radius", "8px")));

            // Text Column
            componentArray.put(new JSONObject()
                    .put("id", textColId)
                    .put("component", "Column")
                    .put("children", new JSONArray().put(itemTitleId).put(itemSubId))
                    .put("styles", new JSONObject().put("gap", "4px")));

            // Title
            componentArray.put(new JSONObject()
                    .put("id", itemTitleId)
                    .put("component", "Text")
                    .put("text", item.optString("title", ""))
                    .put("variant", "body")
                    .put("styles", new JSONObject()
                            .put("font-weight", "bold")
                            .put("text-align", "left")));

            // Subtitle
            componentArray.put(new JSONObject()
                    .put("id", itemSubId)
                    .put("component", "Text")
                    .put("text", item.optString("subtitle", ""))
                    .put("variant", "body")
                    .put("styles", new JSONObject()
                            .put("text-align", "left")
                            .put("color", "#00000099")));
        }

        JSONObject createSurface = buildCreateSurface(surfaceId);
        JSONObject updateComponents = new JSONObject()
                .put("version", VERSION)
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", surfaceId)
                        .put("components", componentArray));

        return new String[]{createSurface.toString(), updateComponents.toString(), "{}"};
    }

    private static CardRenderResult renderFallback(String cardDataJson, List<String> errors) {
        // Generate a minimal error_fallback A2UI card that always passes A2uiJsonValidator
        String surfaceId = "card_fallback";
        String errorSummary = errors.isEmpty() ? "Unknown error" : errors.get(0);
        if (errorSummary.length() > 50) {
            errorSummary = errorSummary.substring(0, 50) + "...";
        }

        try {
            JSONObject createSurface = buildCreateSurface(surfaceId);
            JSONObject updateComponents = new JSONObject()
                    .put("version", VERSION)
                    .put("updateComponents", new JSONObject()
                            .put("surfaceId", surfaceId)
                            .put("components", new JSONArray()
                                    .put(new JSONObject()
                                            .put("id", "root")
                                            .put("component", "Column")
                                            .put("children", new JSONArray().put("fallback-card"))
                                            .put("align", "stretch")
                                            .put("styles", new JSONObject()
                                                    .put("padding", "20px")
                                                    .put("gap", "16px")
                                                    .put("background-color", "#F5F5F5")))
                                    .put(new JSONObject()
                                            .put("id", "fallback-card")
                                            .put("component", "Card")
                                            .put("child", "fallback-content")
                                            .put("styles", new JSONObject()
                                                    .put("width", "auto")
                                                    .put("height", "auto")
                                                    .put("padding", "24px")
                                                    .put("border-radius", "16px")
                                                    .put("background-color", "#FFFFFF")))
                                    .put(new JSONObject()
                                            .put("id", "fallback-content")
                                            .put("component", "Column")
                                            .put("children", new JSONArray().put("fb-title").put("fb-msg"))
                                            .put("align", "stretch"))
                                    .put(new JSONObject()
                                            .put("id", "fb-title")
                                            .put("component", "Text")
                                            .put("text", "Card Data Error")
                                            .put("variant", "h2")
                                            .put("styles", new JSONObject()
                                                    .put("text-align", "left")
                                                    .put("color", "#B00020")))
                                    .put(new JSONObject()
                                            .put("id", "fb-msg")
                                            .put("component", "Text")
                                            .put("text", errorSummary)
                                            .put("variant", "body")
                                            .put("styles", new JSONObject()
                                                    .put("text-align", "left")
                                                    .put("color", "#000000E6")))));

            return new CardRenderResult(false,
                    new String[]{createSurface.toString(), updateComponents.toString(), "{}"},
                    errors, new ArrayList<>());
        } catch (Exception e) {
            // Absolute minimal fallback
            return new CardRenderResult(false,
                    new String[]{
                            "{\"version\":\"v0.9\",\"createSurface\":{\"surfaceId\":\"card_fallback\",\"catalogId\":\"" + CATALOG_ID + "\"}}",
                            "{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"card_fallback\",\"components\":[{\"id\":\"root\",\"component\":\"Text\",\"text\":\"Error: " + errorSummary + "\"}]}}",
                            "{}"
                    },
                    errors, new ArrayList<>());
        }
    }

    private static JSONObject buildCreateSurface(String surfaceId) throws Exception {
        return new JSONObject()
                .put("version", VERSION)
                .put("createSurface", new JSONObject()
                        .put("surfaceId", surfaceId)
                        .put("catalogId", CATALOG_ID));
    }
}