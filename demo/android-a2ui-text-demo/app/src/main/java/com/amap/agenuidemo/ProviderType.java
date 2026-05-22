package com.amap.agenuidemo;

public enum ProviderType {
    MOCK("Mock"),
    FIXTURE("Fixture"),
    LLM("LLM");

    private final String displayName;

    ProviderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
