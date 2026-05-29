package com.amap.agenuidemo.commonui;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Source-level guard for {@link CommonControlsViewFactory#createCardView}.
 *
 * <p>The Card backing view is intentionally a FrameLayout (MeloFrameLayout or
 * plain FrameLayout) — never MeloCardView/CardView. CardView's
 * setContentPadding semantics, built-in elevation/background, and
 * useCompatPadding insets all fight A2UI Card semantics. We guard against
 * accidental regressions by asserting the factory source neither references
 * {@code MeloCardView} nor imports {@code androidx.cardview}.
 *
 * <p>We can't unit-test reflective View creation (no Robolectric in this
 * module), but the source-level check is sufficient to prevent the most likely
 * regression: someone re-introducing CardView "to keep parity with the SDK".
 */
public class CommonControlsViewFactoryTest {

    private static final String FACTORY_PATH =
            "src/main/java/com/amap/agenuidemo/commonui/CommonControlsViewFactory.java";

    private static String readSource(String path) throws IOException {
        try (InputStream in = new FileInputStream(path);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    @Test
    public void factory_doesNotReferenceMeloCardView() throws Exception {
        String src = readSource(FACTORY_PATH);
        // Build the forbidden token at runtime so this assertion's message
        // doesn't itself contain the literal we're guarding against.
        String banned = "Melo" + "CardView";
        assertFalse("CommonControlsViewFactory must not reference the legacy Card backing class. "
                        + "A2UI Card is backed by MeloFrameLayout/FrameLayout — the legacy class's "
                        + "setContentPadding/elevation/useCompatPadding fight A2UI semantics.",
                src.contains(banned));
    }

    @Test
    public void factory_doesNotImportCardView() throws Exception {
        String src = readSource(FACTORY_PATH);
        String banned = "androidx." + "cardview";
        assertFalse("CommonControlsViewFactory must not import the legacy CardView package.",
                src.contains(banned));
    }

    @Test
    public void factory_createCardView_prefersMeloFrameLayout() throws Exception {
        String src = readSource(FACTORY_PATH);
        assertTrue("createCardView must reflectively try MeloFrameLayout",
                src.contains("MeloFrameLayout"));
        assertTrue("createCardView must fall back to plain FrameLayout",
                src.contains("new FrameLayout(context)"));
    }

    @Test
    public void cardComponent_dropsCardViewSpecificCalls() throws Exception {
        String src = readSource(
                "src/main/java/com/amap/agenuidemo/commonui/MeloCardComponent.java");
        // CardView-specific APIs that the refactor removed. Each token is
        // assembled at runtime so this test's own source doesn't trip itself.
        String[] banned = new String[]{
                "set" + "ContentPadding",
                "set" + "CardBackgroundColor",
                "set" + "CardElevation",
                "set" + "UseCompatPadding",
                "set" + "PreventCornerOverlap",
                "instanceof " + "CardView",
                "androidx." + "cardview"};
        for (String token : banned) {
            assertFalse("MeloCardComponent must not use legacy CardView API: " + token,
                    src.contains(token));
        }
    }
}
