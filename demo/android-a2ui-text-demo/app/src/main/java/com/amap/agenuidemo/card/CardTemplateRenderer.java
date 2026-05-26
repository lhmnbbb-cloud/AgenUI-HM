package com.amap.agenuidemo.card;

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
                    messages = renderSportsScoreList(data);
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

    private static String[] renderSportsScoreList(JSONObject data) throws Exception {
        String surfaceId = "card_" + data.optString("requestId", "unknown");
        String title = data.getString("title");
        JSONArray items = data.optJSONArray("items");

        String subtitle = data.optString("subtitle", "");
        String updatedAt = data.optString("updatedAt", "");
        boolean hasSubtitleLine = !subtitle.isEmpty() || !updatedAt.isEmpty();

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

        // Title
        componentArray.put(new JSONObject()
                .put("id", "title-text")
                .put("component", "Text")
                .put("text", title)
                .put("variant", "h2")
                .put("styles", new JSONObject()
                        .put("text-align", "left")
                        .put("font-weight", "bold")));

        // Subtitle line (optional)
        if (hasSubtitleLine) {
            rootChildren.put("subtitle-text");
            StringBuilder subText = new StringBuilder();
            if (!subtitle.isEmpty()) subText.append(subtitle);
            if (!subtitle.isEmpty() && !updatedAt.isEmpty()) subText.append(" · ");
            if (!updatedAt.isEmpty()) subText.append(updatedAt);
            componentArray.put(new JSONObject()
                    .put("id", "subtitle-text")
                    .put("component", "Text")
                    .put("text", subText.toString())
                    .put("variant", "body")
                    .put("styles", new JSONObject()
                            .put("text-align", "left")
                            .put("color", "#00000099")));
        }

        // Empty state
        if (items == null || items.length() == 0) {
            rootChildren.put("empty-card");
            componentArray.put(new JSONObject()
                    .put("id", "empty-card")
                    .put("component", "Card")
                    .put("child", "empty-content")
                    .put("styles", new JSONObject()
                            .put("width", "auto")
                            .put("height", "auto")
                            .put("padding", "24px")
                            .put("border-radius", "16px")
                            .put("background-color", "#FFFFFF")));
            componentArray.put(new JSONObject()
                    .put("id", "empty-content")
                    .put("component", "Column")
                    .put("children", new JSONArray().put("empty-msg"))
                    .put("align", "stretch")
                    .put("styles", new JSONObject().put("gap", "8px")));
            componentArray.put(new JSONObject()
                    .put("id", "empty-msg")
                    .put("component", "Text")
                    .put("text", "今日暂无赛况")
                    .put("variant", "body")
                    .put("styles", new JSONObject()
                            .put("text-align", "center")
                            .put("color", "#00000099")));
        } else {
            // Game cards
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String cardId = "game_card_" + i;
                String colId = "game_col_" + i;
                String statusRowId = "status_row_" + i;
                String statusTextId = "status_text_" + i;
                String summaryTextId = "summary_text_" + i;
                String scoreRowId = "score_row_" + i;
                String awayColId = "away_col_" + i;
                String awayNameId = "away_name_" + i;
                String awayScoreId = "away_score_" + i;
                String homeColId = "home_col_" + i;
                String homeNameId = "home_name_" + i;
                String homeScoreId = "home_score_" + i;

                JSONObject homeTeam = item.optJSONObject("homeTeam");
                JSONObject awayTeam = item.optJSONObject("awayTeam");
                String status = item.optString("status", "");
                String summary = item.optString("summary", "");
                String startTime = item.optString("startTime", "");

                rootChildren.put(cardId);

                // Card
                componentArray.put(new JSONObject()
                        .put("id", cardId)
                        .put("component", "Card")
                        .put("child", colId)
                        .put("styles", new JSONObject()
                                .put("width", "auto")
                                .put("height", "auto")
                                .put("padding", "16px")
                                .put("border-radius", "12px")
                                .put("background-color", "#FFFFFF")));

                // Column: status row + score row
                JSONArray colChildren = new JSONArray().put(statusRowId).put(scoreRowId);
                componentArray.put(new JSONObject()
                        .put("id", colId)
                        .put("component", "Column")
                        .put("children", colChildren)
                        .put("align", "stretch")
                        .put("styles", new JSONObject().put("gap", "8px")));

                // Status row: status text + optional summary
                JSONArray statusRowChildren = new JSONArray().put(statusTextId);
                boolean hasSummary = !summary.isEmpty();
                if (hasSummary) {
                    statusRowChildren.put(summaryTextId);
                }
                componentArray.put(new JSONObject()
                        .put("id", statusRowId)
                        .put("component", "Row")
                        .put("children", statusRowChildren)
                        .put("styles", new JSONObject().put("gap", "8px")));

                // Status display text
                String statusDisplay;
                if ("final".equals(status)) {
                    statusDisplay = "FINAL";
                } else if ("live".equals(status)) {
                    statusDisplay = "LIVE ●";
                } else if ("scheduled".equals(status)) {
                    if (!startTime.isEmpty()) {
                        statusDisplay = startTime + " 开赛";
                    } else {
                        statusDisplay = "待开赛";
                    }
                } else if (!status.isEmpty()) {
                    statusDisplay = status;
                } else {
                    statusDisplay = "未知";
                }
                componentArray.put(new JSONObject()
                        .put("id", statusTextId)
                        .put("component", "Text")
                        .put("text", statusDisplay)
                        .put("variant", "body")
                        .put("styles", new JSONObject()
                                .put("font-weight", "bold")
                                .put("text-align", "left")));

                // Summary (optional)
                if (hasSummary) {
                    componentArray.put(new JSONObject()
                            .put("id", summaryTextId)
                            .put("component", "Text")
                            .put("text", summary)
                            .put("variant", "body")
                            .put("styles", new JSONObject()
                                    .put("text-align", "left")
                                    .put("color", "#00000099")));
                }

                // Score row: away col + home col
                componentArray.put(new JSONObject()
                        .put("id", scoreRowId)
                        .put("component", "Row")
                        .put("children", new JSONArray().put(awayColId).put(homeColId))
                        .put("styles", new JSONObject()
                                .put("gap", "0px")
                                .put("align", "stretch")));

                // Away team column
                String awayName = awayTeam != null ? awayTeam.optString("name", "") : "";
                String awayScore = formatScore(awayTeam, status);
                componentArray.put(new JSONObject()
                        .put("id", awayColId)
                        .put("component", "Column")
                        .put("children", new JSONArray().put(awayNameId).put(awayScoreId))
                        .put("styles", new JSONObject().put("gap", "2px").put("align", "stretch")));
                componentArray.put(new JSONObject()
                        .put("id", awayNameId)
                        .put("component", "Text")
                        .put("text", awayName)
                        .put("variant", "body")
                        .put("styles", new JSONObject()
                                .put("font-weight", "bold")
                                .put("text-align", "left")));
                componentArray.put(new JSONObject()
                        .put("id", awayScoreId)
                        .put("component", "Text")
                        .put("text", awayScore)
                        .put("variant", "h2")
                        .put("styles", new JSONObject()
                                .put("text-align", "left")));

                // Home team column
                String homeName = homeTeam != null ? homeTeam.optString("name", "") : "";
                String homeScore = formatScore(homeTeam, status);
                componentArray.put(new JSONObject()
                        .put("id", homeColId)
                        .put("component", "Column")
                        .put("children", new JSONArray().put(homeNameId).put(homeScoreId))
                        .put("styles", new JSONObject().put("gap", "2px").put("align", "stretch")));
                componentArray.put(new JSONObject()
                        .put("id", homeNameId)
                        .put("component", "Text")
                        .put("text", homeName)
                        .put("variant", "body")
                        .put("styles", new JSONObject()
                                .put("font-weight", "bold")
                                .put("text-align", "left")));
                componentArray.put(new JSONObject()
                        .put("id", homeScoreId)
                        .put("component", "Text")
                        .put("text", homeScore)
                        .put("variant", "h2")
                        .put("styles", new JSONObject()
                                .put("text-align", "left")));
            }
        }

        JSONObject createSurface = buildCreateSurface(surfaceId);
        JSONObject updateComponents = new JSONObject()
                .put("version", VERSION)
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", surfaceId)
                        .put("components", componentArray));

        return new String[]{createSurface.toString(), updateComponents.toString(), "{}"};
    }

    private static String formatScore(JSONObject team, String status) {
        if (team == null || !team.has("score") || team.isNull("score")) {
            if ("final".equals(status) || "live".equals(status)) {
                return "--";
            }
            return "-";
        }
        try {
            Object scoreObj = team.get("score");
            if (scoreObj instanceof Number) {
                return String.valueOf(((Number) scoreObj).intValue());
            }
            String strVal = scoreObj.toString();
            if (strVal.isEmpty()) {
                return "final".equals(status) || "live".equals(status) ? "--" : "-";
            }
            return strVal;
        } catch (Exception e) {
            return "final".equals(status) || "live".equals(status) ? "--" : "-";
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