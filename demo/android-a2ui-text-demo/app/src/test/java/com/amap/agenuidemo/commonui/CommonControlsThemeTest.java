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
    public void themeJson_declaresFourButtonVariants() throws Exception {
        JSONObject root = new JSONObject(readThemeJson());
        JSONObject button = root.getJSONObject("default")
                .getJSONObject("Button")
                .getJSONObject("variant")
                .getJSONObject("enum");
        for (String v : new String[]{
                "mluiButtonPrimary", "mluiButtonSecondary",
                "mluiButtonDanger", "mluiButtonGhost"}) {
            assertNotNull("Button variant missing: " + v, button.optJSONObject(v));
        }
    }

    @Test
    public void themeJson_buttonVariantsCarryAppearanceKeys() throws Exception {
        // Each variant must declare melo-button-background AND melo-button-radius
        // so MeloButtonComponent has something to install. ghost may use a
        // transparent hex, but the key must still be present so the component's
        // appearance pipeline runs (otherwise StyleHelper.applyBackground might
        // leave it un-styled).
        JSONObject root = new JSONObject(readThemeJson());
        JSONObject button = root.getJSONObject("default")
                .getJSONObject("Button")
                .getJSONObject("variant")
                .getJSONObject("enum");
        for (String v : new String[]{
                "mluiButtonPrimary", "mluiButtonSecondary",
                "mluiButtonDanger", "mluiButtonGhost"}) {
            JSONObject styles = button.getJSONObject(v).getJSONObject("styles");
            assertTrue(v + " must declare melo-button-background",
                    styles.has("melo-button-background"));
            assertTrue(v + " must declare melo-button-radius",
                    styles.has("melo-button-radius"));
        }
    }

    @Test
    public void themeJson_visibleButtonVariantsKeepFallbackBackgroundColor() throws Exception {
        // When the AAR is absent (color resource resolution fails), the
        // background-color fallback must remain visible for the three "solid"
        // variants. ghost is allowed transparent (#00000000) by design — it's
        // an outline/link-style button.
        JSONObject root = new JSONObject(readThemeJson());
        JSONObject button = root.getJSONObject("default")
                .getJSONObject("Button")
                .getJSONObject("variant")
                .getJSONObject("enum");
        for (String v : new String[]{
                "mluiButtonPrimary", "mluiButtonSecondary", "mluiButtonDanger"}) {
            JSONObject styles = button.getJSONObject(v).getJSONObject("styles");
            String bg = styles.optString("background-color");
            assertFalse(v + " fallback background-color must not be transparent",
                    bg.isEmpty() || bg.equalsIgnoreCase("#00000000")
                            || bg.equalsIgnoreCase("transparent"));
        }
    }

    @Test
    public void themeJson_buttonVariantsDeclareTextColorKey() throws Exception {
        // Each variant must declare melo-button-text-color so MeloButtonComponent
        // can inject the AAR's text-color CSL (enabled × pressed × disabled ×
        // day/night) into child TextViews. Without it, the child Text is stuck
        // with a single flat color that doesn't respond to pressed/disabled state.
        JSONObject root = new JSONObject(readThemeJson());
        JSONObject button = root.getJSONObject("default")
                .getJSONObject("Button")
                .getJSONObject("variant")
                .getJSONObject("enum");
        for (String v : new String[]{
                "mluiButtonPrimary", "mluiButtonSecondary",
                "mluiButtonDanger", "mluiButtonGhost"}) {
            JSONObject styles = button.getJSONObject(v).getJSONObject("styles");
            assertTrue(v + " must declare melo-button-text-color",
                    styles.has("melo-button-text-color"));
        }
    }

    @Test
    public void themeJson_buttonVariantsUseModernSelectorResources() throws Exception {
        // AAR ships two parallel namespaces:
        //   - mlui_button_*_bg_color  →  res/color/ selector (base CSL with
        //                                 enabled/disabled × day/night), the
        //                                 proper Button selector.
        //   - mlui_btn_*_bg_color     →  values flat color with only _day/_night,
        //                                 no base CSL — legacy, requires
        //                                 buildDayNightDrawable() fallback.
        // The theme must point solid Button variants at the modern selector so
        // disabled state and day/night switching come from the AAR directly.
        JSONObject root = new JSONObject(readThemeJson());
        JSONObject button = root.getJSONObject("default")
                .getJSONObject("Button")
                .getJSONObject("variant")
                .getJSONObject("enum");

        assertEquals("primary must use mlui_button_filled_bg_color selector",
                "mlui_button_filled_bg_color",
                button.getJSONObject("mluiButtonPrimary").getJSONObject("styles")
                        .optString("melo-button-background"));
        assertEquals("secondary must use mlui_button_grey_bg_color selector",
                "mlui_button_grey_bg_color",
                button.getJSONObject("mluiButtonSecondary").getJSONObject("styles")
                        .optString("melo-button-background"));
        assertEquals("danger must use mlui_button_warning_bg_color selector",
                "mlui_button_warning_bg_color",
                button.getJSONObject("mluiButtonDanger").getJSONObject("styles")
                        .optString("melo-button-background"));

        // Reject the legacy _btn_*_bg_color namespace anywhere in the Button body.
        // It would route through buildDayNightDrawable() and silently lose disabled
        // state. Note: mlui_btn_*_text_color is a different (AAR-only) family —
        // there is no "mlui_button_*_text_color" alternative, so we don't ban
        // _btn_ wholesale, only the bg_color variant.
        String body = root.getJSONObject("default")
                .getJSONObject("Button").toString().toLowerCase();
        assertFalse("Button variants must not reference legacy mlui_btn_*_bg_color "
                        + "(use the selector-based mlui_button_*_bg_color instead)",
                body.matches("(?s).*mlui_btn_[a-z_]*_bg_color.*"));
    }

    @Test
    public void themeJson_buttonVariantsDoNotPolluteCoreKeys() throws Exception {
        // Demo-private keys must not leak company-private style names like
        // gua_*, melo_text_appearance_button, etc., into the Button enum body
        // (we tolerate only the four melo-button-* keys plus standard A2UI).
        JSONObject root = new JSONObject(readThemeJson());
        String body = root.getJSONObject("default")
                .getJSONObject("Button").toString().toLowerCase();
        assertFalse("Button variants must not reference gua* resources",
                body.contains("gua"));
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