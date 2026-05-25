package com.amap.agenuidemo.card;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads card fixture data from assets/card_fixtures/ directory.
 *
 * Card fixtures are structured card data (not A2UI protocol messages).
 * They are validated by CardContractValidator and rendered by CardTemplateRenderer.
 */
public class CardDataProvider {

    private static final String CARD_FIXTURES_DIR = "card_fixtures";
    private final Context context;
    private final List<String> fixtureNames = new ArrayList<>();
    private String selectedFixture = null;

    public CardDataProvider(Context context) {
        this.context = context;
        discoverFixtures();
        if (!fixtureNames.isEmpty()) {
            selectedFixture = fixtureNames.get(0);
        }
    }

    public List<String> getFixtureNames() {
        return fixtureNames;
    }

    public List<String> getAvailableFixtures() {
        return fixtureNames;
    }

    public String getSelectedFixture() {
        return selectedFixture;
    }

    public void selectFixture(String fixtureName) {
        if (fixtureNames.contains(fixtureName)) {
            selectedFixture = fixtureName;
        }
    }

    public String readFixtureData(String fixtureName) {
        try {
            String path = CARD_FIXTURES_DIR + "/" + fixtureName;
            InputStream is = context.getAssets().open(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Renders a card fixture to A2UI protocol messages.
     *
     * Returns CardRenderResult which is valid on success or contains
     * fallback A2UI on failure — never returns null.
     */
    public CardRenderResult renderFixture(String fixtureName) {
        String data = readFixtureData(fixtureName);
        if (data == null) {
            return new CardRenderResult(false, null,
                    List.of("Cannot read fixture: " + fixtureName));
        }
        return CardTemplateRenderer.render(data);
    }

    private void discoverFixtures() {
        try {
            String[] files = context.getAssets().list(CARD_FIXTURES_DIR);
            if (files != null) {
                for (String file : files) {
                    if (file.endsWith(".json")) {
                        fixtureNames.add(file);
                    }
                }
            }
        } catch (Exception e) {
            // card_fixtures dir doesn't exist yet — will be created later
        }
    }
}