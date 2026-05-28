package com.amap.agenuidemo;

public enum ProviderType {
    MOCK("Mock"),
    FIXTURE("Fixture"),
    CARD_FIXTURE("Card Fixture"),
    CARD_JSON_INPUT("Card JSON"),
    LLM("LLM"),
    CARD_HTTP("Card HTTP");

    private final String displayName;

    ProviderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
