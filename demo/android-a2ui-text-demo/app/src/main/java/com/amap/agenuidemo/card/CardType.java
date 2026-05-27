package com.amap.agenuidemo.card;

/**
 * Supported card types for the Card Fixture provider.
 *
 * Each type defines a structured card data schema that the
 * CardTemplateRenderer deterministically converts to A2UI protocol messages.
 */
public enum CardType {

    TEXT_SUMMARY("text_summary"),
    TEXT_LIST("text_list"),
    IMAGE_TEXT_LIST("image_text_list"),
    SPORTS_SCORE_LIST("sports_score_list"),
    WEATHER_SUMMARY("weather_summary");

    private final String key;

    CardType(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static CardType fromKey(String key) {
        for (CardType ct : values()) {
            if (ct.key.equals(key)) {
                return ct;
            }
        }
        return null;
    }
}