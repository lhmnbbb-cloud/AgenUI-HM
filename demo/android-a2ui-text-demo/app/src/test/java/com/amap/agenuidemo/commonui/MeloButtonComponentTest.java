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
 * Source-level guard for {@link MeloButtonComponent}.
 *
 * <p>The A2UI Button must stay a "FrameLayout container + child component"
 * model so it can host the child Text/Icon and forward action/functionCall
 * clicks. Replacing the backing view with MeloButton / AppCompatButton would
 * break addChild — those are Button (TextView) subclasses, not ViewGroups.
 *
 * <p>Robolectric isn't available in this module, so we assert source contents
 * instead of reflective behavior. Forbidden tokens are concatenated at runtime
 * to keep this test's own source clean.
 */
public class MeloButtonComponentTest {

    private static final String BUTTON_PATH =
            "src/main/java/com/amap/agenuidemo/commonui/MeloButtonComponent.java";
    private static final String FIXTURE_PATH =
            "src/main/assets/fixtures/form_with_action.json";

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
    public void buttonComponent_backingViewIsFrameLayoutContainer() throws Exception {
        String src = readSource(BUTTON_PATH);
        // The container field is typed FrameLayout, created via
        // CommonControlsViewFactory.createFrameLayout (which reflects
        // MeloFrameLayout, falls back to plain FrameLayout).
        assertTrue("Button backing view must be FrameLayout",
                src.contains("FrameLayout buttonContainer"));
        assertTrue("Button must obtain its container via the common-controls factory",
                src.contains("CommonControlsViewFactory.createFrameLayout(context)"));
    }

    @Test
    public void buttonComponent_doesNotUseButtonSubclassAsBackingView() throws Exception {
        String src = readSource(BUTTON_PATH);
        // Build banned tokens at runtime so this assertion's message doesn't
        // itself contain the literals we're guarding against.
        String[] banned = new String[]{
                "Melo" + "Button buttonContainer",
                "App" + "CompatButton buttonContainer",
                "new Melo" + "Button(",
                "new App" + "CompatButton(",
                "extends android.widget." + "Button",
                "new android.widget." + "Button(context)"};
        for (String token : banned) {
            assertFalse("MeloButtonComponent must not use a Button subclass as backing view: "
                            + token,
                    src.contains(token));
        }
    }

    @Test
    public void buttonComponent_preservesChildContainerSemantics() throws Exception {
        String src = readSource(BUTTON_PATH);
        // shouldAutoAddChildView=true tells A2UILayoutComponent to attach
        // child component Views into our container. Removing it (or returning
        // false) would silently break the "Button with child Text" model.
        assertTrue("Button must auto-add its child component view",
                src.contains("shouldAutoAddChildView") && src.contains("return true"));
    }

    @Test
    public void buttonComponent_resolvesPrivateAppearanceKeys() throws Exception {
        String src = readSource(BUTTON_PATH);
        for (String key : new String[]{
                "melo-button-background",
                "melo-button-disabled-background",
                "melo-button-radius"}) {
            assertTrue("Button must recognise private appearance key: " + key,
                    src.contains(key));
        }
        // Foreground overlay: must reference the AAR-shipped drawable name.
        assertTrue("Button must install foreground overlay via mlui_button_foreground",
                src.contains("mlui_button_foreground"));
        // ColorStateList must come BEFORE getColor in the resolution chain so
        // day/night-aware mlui_btn_*_color selectors keep switching.
        int cslAt = src.indexOf("getColorStateList");
        int getColorAt = src.indexOf("ContextCompat.getColor(");
        assertTrue("Button background resolution must try ColorStateList first",
                cslAt > 0 && (getColorAt < 0 || cslAt < getColorAt));
    }

    @Test
    public void buttonComponent_appearanceRunsAfterSuperUpdate() throws Exception {
        String src = readSource(BUTTON_PATH);
        // applyButtonAppearanceIfPresent must run AFTER super.onUpdateProperties
        // so the StateListDrawable wins over StyleHelper.applyBackground /
        // applyBorder defaults — same ordering rule as MeloCardComponent.
        // There is ALSO a mirror call inside createView (before super here, because
        // it's a different method); search for an apply-call that appears after
        // super.onUpdateProperties using indexOf(fromIndex).
        int superAt = src.indexOf("super.onUpdateProperties");
        int applyAfterSuper = src.indexOf("applyButtonAppearanceIfPresent()", superAt);
        assertTrue("super.onUpdateProperties must run before button appearance install",
                superAt > 0 && applyAfterSuper > superAt);
    }

    @Test
    public void buttonComponent_createViewAlsoInstallsAppearance() throws Exception {
        String src = readSource(BUTTON_PATH);
        // Mirror MeloCardComponent: createView(Context, ViewGroup) must call
        // super.createView(...) first then applyButtonAppearanceIfPresent(),
        // so the initial-create path (e.g. direct Surface.addComponent with no
        // follow-up updateProperties) still gets the public visual.
        assertTrue("Button must override createView(Context, ViewGroup)",
                src.contains("public View createView(Context context, ViewGroup parent)"));
        int superCreate = src.indexOf("super.createView(");
        int applyAfterCreate = src.indexOf("applyButtonAppearanceIfPresent()", superCreate);
        assertTrue("super.createView(...) must run before button appearance install in createView",
                superCreate > 0 && applyAfterCreate > superCreate);
    }

    @Test
    public void buttonComponent_mergesChecksResultAndDisableBeforeSet() throws Exception {
        String src = readSource(BUTTON_PATH);
        // Both inputs must be folded into a single enabled flag and applied
        // once — otherwise a later `disable:false` clobbers an earlier
        // `checks.result:false`, leaving a button that should be locked still
        // firing Function Calls. Guard the exact merged-expression shape.
        assertTrue("enabled must be computed as !isDisabled && checksResult",
                src.contains("enabled = !isDisabled && checksResult"));
        // Defaults must be the safe ones: missing checks → result true,
        // missing disable → false. Both initialised explicitly above the if.
        assertTrue("checksResult must default to true when checks is missing",
                src.contains("boolean checksResult = true"));
        assertTrue("isDisabled must default to false when disable is missing",
                src.contains("boolean isDisabled = false"));
        // The combined enabled flag must drive a single setClickable(enabled)
        // + single setEnabled(enabled) on the button container. More than
        // one of either form signals a regression to the old "set per input"
        // pattern that caused the bug. (Note: the recursive CSL helper also
        // calls v.setClickable(false)/v.setFocusable(false) to suppress
        // child intercept of the pressed state — those are NOT setClickable(enabled),
        // so they don't affect this count.)
        assertTrue("setClickable(enabled) must be the only enabled-driven setClickable call",
                countOccurrences(src, "setClickable(enabled)") == 1);
        assertTrue("setEnabled(enabled) must be the only setEnabled call",
                countOccurrences(src, "setEnabled(enabled)") == 1
                        && countOccurrences(src, "setEnabled(") == 1);
    }

    @Test
    public void buttonComponent_defaultRadiusAlignsWithAar() throws Exception {
        String src = readSource(BUTTON_PATH);
        // AAR's mlui_corner_radius_r3 = 12dp. DEFAULT_RADIUS must produce
        // 12dp on screen. StyleHelper.parseDimension treats "px" as half-dp
        // (standardUnitToPx divides by 2), so "24px" → 12dp physical.
        // Suffix "dp" is NOT recognised by parseDimension.
        assertTrue("DEFAULT_RADIUS must be 24px (→ 12dp after StyleHelper /2)",
                src.contains("DEFAULT_RADIUS = \"24px\""));
    }

    @Test
    public void buttonComponent_installsForegroundOverlayViaSetForeground() throws Exception {
        String src = readSource(BUTTON_PATH);
        // Pressed overlay must use setForeground(), not a pressed-state entry
        // in the background StateListDrawable. This mirrors AAR's
        // Mlui.Button which uses android:foreground for pressed UX.
        assertTrue("Button must call setForeground for pressed overlay",
                src.contains("buttonContainer.setForeground("));
        // The old darken() derivation must be gone — it conflicts with the
        // foreground overlay approach.
        assertFalse("Button must not use darken() for pressed state",
                src.contains("darken(normal"));
        // No melo-button-pressed-background key — pressed is handled by
        // foreground overlay now.
        assertFalse("Button must not define melo-button-pressed-background key",
                src.contains("melo-button-pressed-background"));
    }

    @Test
    public void buttonComponent_injectsTextColorCslIntoChildren() throws Exception {
        String src = readSource(BUTTON_PATH);
        // Recognise the private key.
        assertTrue("Button must recognise melo-button-text-color key",
                src.contains("melo-button-text-color"));
        // Must use an OnHierarchyChangeListener so the CSL injection fires
        // when the framework's auto-add path lands the child view inside our
        // container. onChildViewCreated() is NOT called when
        // shouldAutoAddChildView()==true (Surface only fires it for the
        // manages-internally branch), so we cannot hook there.
        assertTrue("Button must install an OnHierarchyChangeListener for CSL injection",
                src.contains("setOnHierarchyChangeListener("));
        assertTrue("Button must inject CSL in onChildViewAdded callback",
                src.contains("onChildViewAdded(View parent, View child)"));
        // The CSL stamp must be deferred via post() so it runs AFTER the
        // child Text's setTextAppearance() (mluiLabel carries
        // android:textColor=mlui_text_color_secondary which would otherwise
        // clobber the button's CSL).
        assertTrue("CSL injection must be deferred via post()",
                src.contains("child.post(MeloButtonComponent.this::applyTextColorToChildren)")
                        || src.contains(".post(this::applyTextColorToChildren)"));
        // ColorStateList path is the canonical resolution — getColor() would
        // collapse to one int and break pressed/disabled/day-night switching.
        assertTrue("Button text color must resolve via ColorStateList path",
                src.contains("ContextCompat.getColorStateList("));
        // CSL stamping must recurse — the child component may be a wrapper
        // (e.g. Row) and the TextView a grandchild.
        assertTrue("Button must walk children recursively to stamp CSL",
                src.contains("applyCslRecursive("));
        assertTrue("Recursive walk must target TextView only",
                src.contains("instanceof TextView"));
        // Child Views must mirror the button container's drawable state so
        // the CSL sees pressed/enabled and can switch colors. Without this,
        // the text CSL is stuck on normal color even when the button is
        // pressed or disabled.
        assertTrue("Child views must setDuplicateParentStateEnabled(true)",
                src.contains("setDuplicateParentStateEnabled(true)"));
        // The child TextView must not be clickable/focusable itself — it
        // would intercept the pressed state and prevent the parent (button
        // container) from reaching state_pressed, breaking the CSL switch.
        assertTrue("Child TextView must be setClickable(false)",
                src.contains("v.setClickable(false)"));
        assertTrue("Child TextView must be setFocusable(false)",
                src.contains("v.setFocusable(false)"));
        // Update path must also defer the injection via post().
        assertTrue("onUpdateProperties must post applyTextColorToChildren",
                src.contains("buttonContainer.post(this::applyTextColorToChildren)"));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    @Test
    public void formFixture_submitButtonUsesMluiButtonPrimary() throws Exception {
        String fixture = readSource(FIXTURE_PATH);
        assertTrue("submit-btn should adopt mluiButtonPrimary variant",
                fixture.contains("\"variant\": \"mluiButtonPrimary\""));
        // Child Text must use a Mlui text variant (mluiLabel by design).
        assertTrue("submit-btn child Text should use mluiLabel variant",
                fixture.contains("\"variant\": \"mluiLabel\""));
    }

    @Test
    public void formFixture_buttonStillTriggersToastFunctionCall() throws Exception {
        // Click → Function Call must not regress. Toast is the allowlisted
        // demo function; if anyone removes it the Button visual sample becomes
        // pure decoration.
        String fixture = readSource(FIXTURE_PATH);
        assertTrue("submit-btn must still declare a functionCall action",
                fixture.contains("\"functionCall\""));
        assertTrue("submit-btn functionCall must still call toast",
                fixture.contains("\"call\": \"toast\""));
    }
}
