package com.amap.agenuidemo.card;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates structured card data against the card contract schema.
 *
 * Required fields for all card types:
 * - requestId (string, non-empty)
 * - cardType (string, must be a known CardType key)
 * - title (string, non-empty)
 *
 * Card type-specific requirements:
 * - text_summary: content (string, non-empty)
 * - text_list: items (JSONArray, non-empty, each with text field)
 * - image_text_list: items (JSONArray, non-empty, each with imageUrl, title, subtitle)
 */
public class CardContractValidator {

    public static ValidationResult validate(String cardDataJson) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (cardDataJson == null || cardDataJson.trim().isEmpty()) {
            errors.add("Card data is null or empty");
            return new ValidationResult(false, errors, warnings);
        }

        JSONObject data;
        try {
            data = new JSONObject(cardDataJson);
        } catch (Exception e) {
            errors.add("Invalid JSON: " + e.getMessage());
            return new ValidationResult(false, errors, warnings);
        }

        // requestId
        String requestId = data.optString("requestId", "");
        if (requestId.isEmpty()) {
            errors.add("Missing or empty 'requestId'");
        }

        // cardType
        String cardTypeKey = data.optString("cardType", "");
        if (cardTypeKey.isEmpty()) {
            errors.add("Missing or empty 'cardType'");
        } else {
            CardType cardType = CardType.fromKey(cardTypeKey);
            if (cardType == null) {
                errors.add("Unknown cardType: '" + cardTypeKey + "'. Supported: text_summary, text_list, image_text_list, sports_score_list");
            } else {
                validateCardTypeFields(data, cardType, errors, warnings);
            }
        }

        // title
        String title = data.optString("title", "");
        if (title.isEmpty()) {
            errors.add("Missing or empty 'title'");
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private static void validateCardTypeFields(JSONObject data, CardType cardType,
                                                 List<String> errors, List<String> warnings) {
        switch (cardType) {
            case TEXT_SUMMARY:
                validateTextSummary(data, errors, warnings);
                break;
            case TEXT_LIST:
                validateTextList(data, errors, warnings);
                break;
            case IMAGE_TEXT_LIST:
                validateImageTextList(data, errors, warnings);
                break;
            case SPORTS_SCORE_LIST:
                validateSportsScoreList(data, errors, warnings);
                break;
        }
    }

    private static void validateTextSummary(JSONObject data, List<String> errors, List<String> warnings) {
        String content = data.optString("content", "");
        if (content.isEmpty()) {
            errors.add("text_summary: missing or empty 'content'");
        }
    }

    private static void validateTextList(JSONObject data, List<String> errors, List<String> warnings) {
        JSONArray items = data.optJSONArray("items");
        if (items == null || items.length() == 0) {
            errors.add("text_list: missing or empty 'items'");
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                errors.add("text_list: items[" + i + "] is not a JSON object");
                continue;
            }
            String text = item.optString("text", "");
            if (text.isEmpty()) {
                warnings.add("text_list: items[" + i + "] has empty 'text'");
            }
        }
        if (items.length() > 20) {
            warnings.add("text_list: items count " + items.length() + " exceeds recommended max 20");
        }
    }

    private static void validateImageTextList(JSONObject data, List<String> errors, List<String> warnings) {
        JSONArray items = data.optJSONArray("items");
        if (items == null || items.length() == 0) {
            errors.add("image_text_list: missing or empty 'items'");
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                errors.add("image_text_list: items[" + i + "] is not a JSON object");
                continue;
            }
            String imageUrl = item.optString("imageUrl", "");
            if (imageUrl.isEmpty()) {
                errors.add("image_text_list: items[" + i + "] missing 'imageUrl'");
            }
            String title = item.optString("title", "");
            if (title.isEmpty()) {
                warnings.add("image_text_list: items[" + i + "] has empty 'title'");
            }
            String subtitle = item.optString("subtitle", "");
            if (subtitle.isEmpty()) {
                warnings.add("image_text_list: items[" + i + "] has empty 'subtitle'");
            }
        }
        if (items.length() > 10) {
            warnings.add("image_text_list: items count " + items.length() + " exceeds recommended max 10");
        }
    }

    private static final java.util.Set<String> KNOWN_STATUSES = java.util.Set.of("final", "live", "scheduled");

    private static void validateSportsScoreList(JSONObject data, List<String> errors, List<String> warnings) {
        JSONArray items = data.optJSONArray("items");
        if (items == null) {
            errors.add("sports_score_list: missing 'items'");
            return;
        }
        // Empty items is valid — renders empty state, not fallback
        if (items.length() == 0) {
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                errors.add("sports_score_list: items[" + i + "] is not a JSON object");
                continue;
            }

            // homeTeam.name required
            JSONObject homeTeam = item.optJSONObject("homeTeam");
            if (homeTeam == null) {
                errors.add("sports_score_list: items[" + i + "] missing 'homeTeam'");
            } else if (homeTeam.optString("name", "").isEmpty()) {
                errors.add("sports_score_list: items[" + i + "] homeTeam.name is empty");
            }

            // awayTeam.name required
            JSONObject awayTeam = item.optJSONObject("awayTeam");
            if (awayTeam == null) {
                errors.add("sports_score_list: items[" + i + "] missing 'awayTeam'");
            } else if (awayTeam.optString("name", "").isEmpty()) {
                errors.add("sports_score_list: items[" + i + "] awayTeam.name is empty");
            }

            // Unknown status = warning, not error
            String status = item.optString("status", "");
            if (!status.isEmpty() && !KNOWN_STATUSES.contains(status)) {
                warnings.add("sports_score_list: items[" + i + "] unknown status '" + status + "'");
            }

            // final/live missing/null/empty score = warning
            if ("final".equals(status) || "live".equals(status)) {
                if (homeTeam != null && !hasEffectiveScore(homeTeam)) {
                    warnings.add("sports_score_list: items[" + i + "] " + status + " game missing homeTeam.score");
                }
                if (awayTeam != null && !hasEffectiveScore(awayTeam)) {
                    warnings.add("sports_score_list: items[" + i + "] " + status + " game missing awayTeam.score");
                }
            }
        }
        if (items.length() > 20) {
            warnings.add("sports_score_list: items count " + items.length() + " exceeds recommended max 20");
        }
    }

    private static boolean hasEffectiveScore(JSONObject team) {
        if (!team.has("score") || team.isNull("score")) {
            return false;
        }
        Object val = team.opt("score");
        if (val instanceof String && ((String) val).isEmpty()) {
            return false;
        }
        return true;
    }

    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }
}