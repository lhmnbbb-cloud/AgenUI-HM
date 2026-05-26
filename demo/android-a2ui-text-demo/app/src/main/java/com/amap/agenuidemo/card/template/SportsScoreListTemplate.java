package com.amap.agenuidemo.card.template;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Template that converts sports_score_list CardData to A2UI v0.9 messages.
 *
 * Delegated from CardTemplateRenderer for SPORTS_SCORE_LIST card type.
 * Output is always 3 messages: [createSurface, updateComponents, "{}"].
 */
public class SportsScoreListTemplate {

    private static final String VERSION = "v0.9";
    private static final String CATALOG_ID = "https://a2ui.org/specification/v0_9/standard_catalog.json";

    public static String[] render(JSONObject data) throws Exception {
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
                addGameCard(componentArray, rootChildren, item, i);
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

    private static void addGameCard(JSONArray componentArray, JSONArray rootChildren,
                                     JSONObject item, int index) throws Exception {
        String cardId = "game_card_" + index;
        String colId = "game_col_" + index;
        String statusRowId = "status_row_" + index;
        String statusTextId = "status_text_" + index;
        String summaryTextId = "summary_text_" + index;
        String scoreRowId = "score_row_" + index;
        String awayColId = "away_col_" + index;
        String awayNameId = "away_name_" + index;
        String awayScoreId = "away_score_" + index;
        String homeColId = "home_col_" + index;
        String homeNameId = "home_name_" + index;
        String homeScoreId = "home_score_" + index;

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
        String statusDisplay = formatStatus(status, startTime);
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

    static String formatScore(JSONObject team, String status) {
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

    static String formatStatus(String status, String startTime) {
        if ("final".equals(status)) {
            return "FINAL";
        } else if ("live".equals(status)) {
            return "LIVE ●";
        } else if ("scheduled".equals(status)) {
            if (startTime != null && !startTime.isEmpty()) {
                return startTime + " 开赛";
            }
            return "待开赛";
        } else if (status != null && !status.isEmpty()) {
            return status;
        }
        return "未知";
    }

    private static JSONObject buildCreateSurface(String surfaceId) throws Exception {
        return new JSONObject()
                .put("version", VERSION)
                .put("createSurface", new JSONObject()
                        .put("surfaceId", surfaceId)
                        .put("catalogId", CATALOG_ID));
    }
}
