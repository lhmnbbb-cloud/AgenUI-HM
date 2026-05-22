package com.amap.agenuidemo;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FixtureUiGenerationProvider implements UiGenerationProvider {

    private static final String TAG = "FixtureProvider";
    private static final String FIXTURES_DIR = "fixtures";

    private final Context context;
    private String selectedFixture;
    private List<String> availableFixtures;

    public FixtureUiGenerationProvider(Context context) {
        this.context = context.getApplicationContext();
        this.availableFixtures = discoverFixtures();
        if (!availableFixtures.isEmpty()) {
            this.selectedFixture = availableFixtures.get(0);
        }
    }

    public List<String> getAvailableFixtures() {
        return Collections.unmodifiableList(availableFixtures);
    }

    public void selectFixture(String fixtureName) {
        if (availableFixtures.contains(fixtureName)) {
            this.selectedFixture = fixtureName;
        } else {
            Log.w(TAG, "Fixture not found: " + fixtureName);
        }
    }

    public String getSelectedFixture() {
        return selectedFixture;
    }

    @Override
    public String[] generate(String userInput) {
        if (selectedFixture == null) {
            Log.e(TAG, "No fixture selected");
            return new String[]{"{}", "{}", "{}"};
        }
        try {
            return readFixture(selectedFixture);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read fixture: " + selectedFixture, e);
            return new String[]{"{}", "{}", "{}"};
        }
    }

    private List<String> discoverFixtures() {
        List<String> fixtures = new ArrayList<>();
        try {
            String[] files = context.getAssets().list(FIXTURES_DIR);
            if (files != null) {
                for (String file : files) {
                    if (file.endsWith(".json")) {
                        fixtures.add(file.substring(0, file.length() - 5));
                    }
                }
                Collections.sort(fixtures);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to discover fixtures", e);
        }
        Log.d(TAG, "Discovered fixtures: " + fixtures);
        return fixtures;
    }

    private String[] readFixture(String fixtureName) throws Exception {
        String path = FIXTURES_DIR + "/" + fixtureName + ".json";
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(path), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();

        JSONArray array = new JSONArray(sb.toString());
        String[] messages = new String[3];
        for (int i = 0; i < 3; i++) {
            if (i < array.length()) {
                messages[i] = array.getJSONObject(i).toString();
            } else {
                messages[i] = "{}";
            }
        }
        return messages;
    }
}
