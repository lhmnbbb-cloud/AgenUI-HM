package com.amap.agenuidemo.commonui;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Guards the demo-side common_controls_theme.json so future edits cannot
 * accidentally introduce {@code gua*} branded resources (which are not part
 * of the agreed Card appearance contract) and cannot drop the established
 * {@code mlui*} Text variants.
 */
public class CommonControlsThemeTest {

    private static final String THEME_JSON_PATH =
            "src/main/assets/common_controls_theme.json";

    private String readThemeJson() throws IOException {
        try (InputStream in = new FileInputStream(THEME_JSON_PATH);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    @Test
    public void themeJson_doesNotContainGuaResources() throws Exception {
        String raw = readThemeJson();
        assertFalse("common_controls_theme.json must not reference any gua* resources. "
                        + "Use mlui_app_bg_color_* (color) + Android-side GradientDrawable instead.",
                raw.toLowerCase().contains("gua"));
    }

    @Test
    public void themeJson_preservesMluiTextVariants() throws Exception {
        JSONObject root = new JSONObject(readThemeJson());
        JSONObject text = root.getJSONObject("default")
                .getJSONObject("Text")
                .getJSONObject("variant")
                .getJSONObject("enum");
        for (String v : new String[]{
                "mluiTitleLarge", "mluiTitle", "mluiBody", "mluiContent", "mluiLabel"}) {
            assertNotNull("Text variant missing: " + v, text.optJSONObject(v));
        }
    }

    @Test
    public void themeJson_declaresFourCardVariants() throws Exception {
        JSONObject root = new JSONObject(readThemeJson());
        JSONObject card = root.getJSONObject("default")
                .getJSONObject("Card")
                .getJSONObject("variant")
                .getJSONObject("enum");
        for (String v : new String[]{
                "mluiCardPrimary", "mluiCardSecondary", "mluiCardTertiary", "mluiCardRect"}) {
            assertNotNull("Card variant missing: " + v, card.optJSONObject(v));
        }
    }

    @Test
    public void themeJson_cardVariantsKeepVisibleFallbackDecoration() throws Exception {
        // When the proprietary AAR is absent, MeloCardComponent can't resolve
        // melo-card-background and the C++ spec-merged background-color /
        // border-radius become the visible fallback. We deliberately keep them
        // visible (white, rounded) instead of transparent so unbranded builds
        // still show a usable card.
        JSONObject root = new JSONObject(readThemeJson());
        JSONObject card = root.getJSONObject("default")
                .getJSONObject("Card")
                .getJSONObject("variant")
                .getJSONObject("enum");
        for (String v : new String[]{
                "mluiCardPrimary", "mluiCardSecondary", "mluiCardTertiary", "mluiCardRect"}) {
            JSONObject styles = card.getJSONObject(v).getJSONObject("styles");
            assertTrue(v + " must declare melo-card-background",
                    styles.has("melo-card-background"));
            assertTrue(v + " must declare melo-card-radius",
                    styles.has("melo-card-radius"));
            assertEquals(v + " fallback background-color must be visible white",
                    "#FFFFFF", styles.optString("background-color"));
            assertEquals(v + " filter must be none (no default shadow)",
                    "none", styles.optString("filter"));
        }

        for (String v : new String[]{
                "mluiCardPrimary", "mluiCardSecondary", "mluiCardTertiary"}) {
            assertEquals(v + " fallback border-radius must be 16px",
                    "16px",
                    card.getJSONObject(v).getJSONObject("styles").optString("border-radius"));
        }
        assertEquals("mluiCardRect fallback border-radius must be 0px",
                "0px",
                card.getJSONObject("mluiCardRect").getJSONObject("styles").optString("border-radius"));
    }
}