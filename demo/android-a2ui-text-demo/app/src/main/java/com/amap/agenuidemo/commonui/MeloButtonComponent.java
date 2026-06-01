package com.amap.agenuidemo.commonui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.amap.agenui.render.component.A2UILayoutComponent;
import com.amap.agenui.render.style.StyleHelper;

import java.util.Map;

/**
 * A2UI Button component backed by a MeloFrameLayout container.
 *
 * <p>First-version limitation: Button uses a MeloFrameLayout (or FrameLayout
 * fallback) as the container, NOT MeloButton/AppCompatButton. MeloButton is a
 * Button subclass, not a ViewGroup, so it cannot hold child components. The
 * standard A2UI Button model is "container + child component" — the child
 * (typically a Text) is rendered separately and added to the Button container.
 * Using a FrameLayout preserves child rendering and Function Call click
 * handling while still leveraging Melo styling.
 *
 * <p>Supports: child, variant, action, disable, checks, and styles
 * (background-color, plus demo-private {@code melo-button-*} keys carried by
 * the {@code mluiButton*} variants registered in {@code common_controls_theme.json}).
 *
 * <p>Appearance key resolution mirrors {@code MeloCardComponent}: the private
 * keys are resolved in {@link #onUpdateProperties} AFTER {@code super}, so the
 * StateListDrawable we install here wins over StyleHelper.applyBackground /
 * applyBorder defaults.
 */
public class MeloButtonComponent extends A2UILayoutComponent {

    private static final String TAG = "MeloButtonComponent";

    private static final String STYLE_KEY_BACKGROUND = "melo-button-background";
    private static final String STYLE_KEY_DISABLED_BACKGROUND = "melo-button-disabled-background";
    private static final String STYLE_KEY_RADIUS = "melo-button-radius";
    private static final String STYLE_KEY_TEXT_COLOR = "melo-button-text-color";

    // Aligned with AAR's mlui_corner_radius_r3 (12dp). Theme variants still
    // win when they declare melo-button-radius explicitly.
    // NOTE: StyleHelper.parseDimension treats "px" as half-dp (standardUnitToPx
    // divides by 2 before density conversion). So "24px" → 12dp on screen.
    // Suffix "dp" is NOT recognised (NumberFormatException → WRAP_CONTENT → 0).
    private static final String DEFAULT_RADIUS = "24px";

    // AAR-shipped foreground selector: state_pressed → semi-transparent
    // overlay using mlui_btn_pressed_foreground_color, 12dp corners.
    // This is the canonical pressed-state UX path on MeloButtonFilled — we
    // install it on the FrameLayout container so MeloFrameLayout / FrameLayout
    // pressed state drives the overlay automatically. Absent (unbranded
    // builds) → setForeground call is skipped and there is no pressed visual.
    private static final String FOREGROUND_RES_NAME = "mlui_button_foreground";

    private Context context;
    private FrameLayout buttonContainer;
    private String childComponentId;

    public MeloButtonComponent(Context context, String id, Map<String, Object> properties) {
        super(id, "Button");
        this.context = context;
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    @Override
    public View createView(Context context, ViewGroup parent) {
        View view = super.createView(context, parent);
        // Mirror MeloCardComponent: install the StateListDrawable on the
        // initial-create path too, so any entry point that skips a follow-up
        // updateProperties (e.g. direct Surface.addComponent) still gets the
        // public visual. Text color injection is driven by the hierarchy
        // listener installed in onCreateView — no need to fire it here, since
        // no child view exists yet.
        applyProperties();
        applyButtonAppearanceIfPresent();
        return view;
    }

    @Override
    protected View onCreateView(Context context) {
        buttonContainer = CommonControlsViewFactory.createFrameLayout(context);
        buttonContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((FrameLayout.LayoutParams) buttonContainer.getLayoutParams()).gravity = Gravity.CENTER;
        installChildTextColorListener();
        return buttonContainer;
    }

    @Override
    public void onUpdateProperties(Map<String, Object> properties) {
        super.onUpdateProperties(properties);
        if (buttonContainer == null) return;
        applyProperties();
        applyButtonAppearanceIfPresent();
        // Posted so we run AFTER any child Text component's pending
        // setTextAppearance() (which would otherwise clobber our CSL with
        // the mluiLabel TextAppearance's android:textColor).
        buttonContainer.post(this::applyTextColorToChildren);
    }

    private void applyProperties() {
        if (buttonContainer == null) return;

        if (properties.containsKey("child")) {
            childComponentId = String.valueOf(properties.get("child"));
        }

        // Compute final enabled state from BOTH inputs before applying.
        // Earlier we set state once per property, so a later `disable:false`
        // would clobber an earlier `checks.result:false` — leaving a button
        // that should be unclickable still firing Function Calls.
        boolean checksResult = true;
        if (properties.containsKey("checks")) {
            Object checksValue = properties.get("checks");
            if (checksValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> checksMap = (Map<String, Object>) checksValue;
                Object resultObj = checksMap.get("result");
                if (resultObj instanceof Boolean) {
                    checksResult = (Boolean) resultObj;
                }
            }
        }

        boolean isDisabled = false;
        if (properties.containsKey("disable")) {
            Object disableValue = properties.get("disable");
            if (disableValue instanceof Boolean) {
                isDisabled = (Boolean) disableValue;
            }
        }

        boolean enabled = !isDisabled && checksResult;
        buttonContainer.setClickable(enabled);
        buttonContainer.setEnabled(enabled);
        // Reset alpha to 1; appearance path handles disabled visual via StateListDrawable.
        // Only fall back to alpha dimming when no melo-button-disabled-background is provided.
        buttonContainer.setAlpha(1.0f);
    }

    /**
     * Resolves the demo-private {@code melo-button-*} appearance keys
     * (carried by {@code mluiButton*} variants in {@code common_controls_theme.json})
     * and installs background + foreground on the container.
     *
     * <p>Background resolution order (per key) — ColorStateList first so
     * day/night-aware {@code mlui_btn_*_color} selectors keep their state
     * switching. {@code getColor()} would lock the button to its day value.
     * <ol>
     *   <li>If the value looks like a hex string ({@code #RRGGBB} / {@code #AARRGGBB})
     *       → parse to color.</li>
     *   <li>Else treat as Android color resource name:
     *       <ol>
     *         <li>{@link ContextCompat#getColorStateList} — preserves day/night.</li>
     *         <li>{@link ContextCompat#getColor} — flat color fallback.</li>
     *         <li>{@link ContextCompat#getDrawable} — last resort.</li>
     *       </ol>
     *   </li>
     * </ol>
     *
     * <p>Pressed state is delivered via {@code android:foreground} overlay
     * (AAR's {@code mlui_button_foreground} selector) rather than a separate
     * pressed-background entry in the StateListDrawable. This mirrors the
     * AAR's own approach on {@code Mlui.Button}: background drawable carries
     * normal + disabled states; foreground carries pressed overlay.
     */
    private void applyButtonAppearanceIfPresent() {
        if (buttonContainer == null) return;
        Map<String, Object> styles = extractStyles(this.properties);
        if (styles == null) return;
        boolean hasNormal = styles.containsKey(STYLE_KEY_BACKGROUND);
        boolean hasDisabled = styles.containsKey(STYLE_KEY_DISABLED_BACKGROUND);
        if (!hasNormal && !hasDisabled) {
            return;
        }

        Context ctx = buttonContainer.getContext();

        Object rawRadius = styles.containsKey(STYLE_KEY_RADIUS)
                ? styles.get(STYLE_KEY_RADIUS) : DEFAULT_RADIUS;
        int radiusPx = StyleHelper.parseDimension(rawRadius, ctx);
        if (radiusPx < 0) radiusPx = 0;

        GradientDrawable normal = hasNormal
                ? buildStateDrawable(ctx, String.valueOf(styles.get(STYLE_KEY_BACKGROUND)), radiusPx)
                : null;
        GradientDrawable disabled = hasDisabled
                ? buildStateDrawable(ctx, String.valueOf(styles.get(STYLE_KEY_DISABLED_BACKGROUND)), radiusPx)
                : null;

        if (normal == null && disabled == null) {
            Log.w(TAG, "melo-button-background not resolvable; leaving fallback decoration");
            return;
        }

        // StateListDrawable: disabled wins over normal (same order as AAR).
        StateListDrawable list = new StateListDrawable();
        if (disabled != null) {
            list.addState(new int[]{-android.R.attr.state_enabled}, disabled);
        }
        if (normal != null) {
            list.addState(new int[]{}, normal);
        }
        buttonContainer.setBackground(list);
        buttonContainer.setFocusable(true);

        // Pressed overlay: AAR's mlui_button_foreground is a selector that
        // shows a semi-transparent fill on state_pressed. Installing it via
        // setForeground() lets the FrameLayout's pressed state drive the
        // overlay independently of the background drawable. The foreground
        // carries its own corner radius (mlui_corner_radius_r3 = 12dp).
        installForegroundOverlay(ctx);
    }

    private void installForegroundOverlay(Context ctx) {
        Resources res = ctx.getResources();
        String pkg = ctx.getPackageName();
        int fgId = res.getIdentifier(FOREGROUND_RES_NAME, "drawable", pkg);
        if (fgId == 0) {
            Log.d(TAG, FOREGROUND_RES_NAME + " not found; pressed overlay skipped");
            return;
        }
        try {
            Drawable fg = ContextCompat.getDrawable(ctx, fgId);
            buttonContainer.setForeground(fg);
        } catch (Throwable t) {
            Log.d(TAG, "Failed to load " + FOREGROUND_RES_NAME + ": " + t.getMessage());
        }
    }

    /**
     * Resolves {@code melo-button-text-color} (Android color resource name OR
     * hex string) and applies it as a {@link ColorStateList} to every child
     * TextView under this button. The CSL carries enabled × pressed × disabled
     * × day/night state, so pressed-text-dim and disabled-text-grey come for
     * free without us having to manage states here.
     *
     * <p>Why ColorStateList over getColor: flattening to a single int would
     * lock the text to its enabled-day value, breaking the pressed/disabled
     * UX the AAR ships. Same rule as the background path.
     *
     * <p>Why a hierarchy listener and not {@code onChildViewCreated()}: the
     * framework's {@code Surface} only fires {@code onChildViewCreated} when
     * {@code shouldAutoAddChildView()} returns {@code false} (it's the
     * "manages children itself" hook, used by Tabs/Modal). We return
     * {@code true} so the framework attaches the child for us — which means
     * the only reliable signal that a child View has actually landed inside
     * our container is {@link android.view.ViewGroup.OnHierarchyChangeListener#onChildViewAdded}.
     *
     * <p>Why a deferred {@code post()}: the child Text component runs its own
     * {@code onUpdateProperties} AFTER it's attached, which applies its
     * {@code mluiLabel} TextAppearance — and that TextAppearance carries
     * {@code android:textColor=@color/mlui_text_color_secondary}, which would
     * clobber the button CSL we just installed. Posting our {@code setTextColor}
     * to the end of the current message queue moves it past the child's update
     * so the button CSL wins. (The child's text is usually short and the layout
     * pass hasn't happened yet, so users don't see a flash of the wrong color.)
     */
    private void installChildTextColorListener() {
        if (buttonContainer == null) return;
        buttonContainer.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                if (child != null) {
                    child.post(MeloButtonComponent.this::applyTextColorToChildren);
                }
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
                // no-op
            }
        });
    }

    private void applyTextColorToChildren() {
        if (buttonContainer == null) return;
        Map<String, Object> styles = extractStyles(this.properties);
        if (styles == null || !styles.containsKey(STYLE_KEY_TEXT_COLOR)) return;
        Object raw = styles.get(STYLE_KEY_TEXT_COLOR);
        if (raw == null) return;
        String value = String.valueOf(raw).trim();
        if (value.isEmpty()) return;

        Context ctx = buttonContainer.getContext();
        ColorStateList csl = resolveTextColorCsl(ctx, value);
        if (csl == null) {
            Log.d(TAG, "melo-button-text-color not resolvable: " + value);
            return;
        }
        // Walk subtree starting from buttonContainer's direct children:
        // buttonContainer is the state source (pressed/enabled live here),
        // so every descendant must mirror it via setDuplicateParentStateEnabled,
        // otherwise the CSL would only ever see "enabled" and never switch
        // to pressed/disabled.
        for (int i = 0; i < buttonContainer.getChildCount(); i++) {
            applyCslRecursive(buttonContainer.getChildAt(i), csl);
        }
    }

    /**
     * Walk the subtree and stamp {@code csl} onto every TextView. Each visited
     * View also opts into {@link View#setDuplicateParentStateEnabled} so its
     * {@code getDrawableState()} mirrors the Button container — without this,
     * the child TextView's CSL never sees {@code state_pressed} /
     * {@code -state_enabled} and the pressed/disabled colors are dead.
     *
     * <p>Recursive because the child component sitting in our FrameLayout may
     * itself be a wrapper (e.g. a Row containing the label Text), in which
     * case the visible TextView is a grandchild.
     */
    private static void applyCslRecursive(View v, ColorStateList csl) {
        if (v == null) return;
        // Mirror parent (button) drawable state so the CSL switches on
        // pressed/enabled. Safe to call on any View.
        v.setDuplicateParentStateEnabled(true);
        if (v instanceof TextView) {
            // Disable focusable/clickable on the TextView, otherwise it could
            // intercept pressed state and break duplicate-parent propagation.
            v.setClickable(false);
            v.setFocusable(false);
            ((TextView) v).setTextColor(csl);
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyCslRecursive(vg.getChildAt(i), csl);
            }
        }
    }

    private static ColorStateList resolveTextColorCsl(Context ctx, String value) {
        if (ctx == null || value == null) return null;
        if (value.startsWith("#")) {
            try {
                return ColorStateList.valueOf(Color.parseColor(value));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        Resources res = ctx.getResources();
        String pkg = ctx.getPackageName();
        int colorId = res.getIdentifier(value, "color", pkg);
        if (colorId == 0) return null;
        try {
            return ContextCompat.getColorStateList(ctx, colorId);
        } catch (Throwable t) {
            return null;
        }
    }

    private static GradientDrawable buildStateDrawable(Context ctx, String value, int radiusPx) {
        GradientDrawable g = buildBackgroundDrawable(ctx, value);
        if (g != null) {
            g.setCornerRadius(radiusPx);
        }
        return g;
    }

    /**
     * Resolves {@code value} (hex string OR Android color/drawable resource name)
     * to a {@link GradientDrawable}. Returns {@code null} only when every
     * strategy fails.
     */
    private static GradientDrawable buildBackgroundDrawable(Context ctx, String value) {
        if (ctx == null || value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;

        if (v.startsWith("#")) {
            try {
                int color = Color.parseColor(v);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.RECTANGLE);
                g.setColor(color);
                return g;
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "Hex parse failed for " + v + ": " + e.getMessage());
                return null;
            }
        }

        Resources res = ctx.getResources();
        String pkg = ctx.getPackageName();

        int colorId = res.getIdentifier(v, "color", pkg);
        // AAR quirk (legacy): mlui_btn_*_bg_color only has _day/_night flat
        // entries with no base CSL. The newer mlui_button_*_bg_color selectors
        // ship a proper base CSL (enabled/disabled × day/night). When the
        // requested base name doesn't exist, try composing a day/night CSL
        // from _day + _night keyed on the AAR's custom mlui_state_night attr.
        if (colorId == 0) {
            GradientDrawable composed = buildDayNightDrawable(ctx, res, pkg, v);
            if (composed != null) return composed;
            colorId = res.getIdentifier(v + "_day", "color", pkg);
        }
        if (colorId != 0) {
            try {
                ColorStateList csl = ContextCompat.getColorStateList(ctx, colorId);
                if (csl != null) {
                    GradientDrawable g = new GradientDrawable();
                    g.setShape(GradientDrawable.RECTANGLE);
                    g.setColor(csl);
                    return g;
                }
            } catch (Throwable t) {
                Log.d(TAG, "ColorStateList lookup failed for " + v + ": " + t.getMessage());
            }
            try {
                int color = ContextCompat.getColor(ctx, colorId);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.RECTANGLE);
                g.setColor(color);
                return g;
            } catch (Throwable t) {
                Log.d(TAG, "ContextCompat.getColor failed for " + v + ": " + t.getMessage());
            }
        }

        int drawableId = res.getIdentifier(v, "drawable", pkg);
        if (drawableId != 0) {
            try {
                Drawable d = ContextCompat.getDrawable(ctx, drawableId);
                if (d instanceof GradientDrawable) {
                    return (GradientDrawable) d.mutate();
                }
                if (d instanceof ColorDrawable) {
                    GradientDrawable g = new GradientDrawable();
                    g.setShape(GradientDrawable.RECTANGLE);
                    g.setColor(((ColorDrawable) d).getColor());
                    return g;
                }
                Log.d(TAG, "Drawable for " + v + " is not a flat color; skipping radius wrap");
            } catch (Throwable t) {
                Log.d(TAG, "ContextCompat.getDrawable failed for " + v + ": " + t.getMessage());
            }
        }

        return null;
    }

    /**
     * Builds a {@link GradientDrawable} carrying a ColorStateList synthesized
     * from {@code <name>_day} + {@code <name>_night} flat color resources,
     * keyed on the AAR's custom {@code mlui_state_night} attribute. Fallback
     * for resource names that only ship day/night flat entries (legacy
     * {@code mlui_btn_*_bg_color}). The preferred {@code mlui_button_*_bg_color}
     * selectors ship a proper base CSL and skip this path entirely.
     * Returns {@code null} if either flat color or the mlui_state_night
     * attribute can't be resolved.
     */
    private static GradientDrawable buildDayNightDrawable(Context ctx, Resources res,
                                                          String pkg, String baseName) {
        int dayId = res.getIdentifier(baseName + "_day", "color", pkg);
        int nightId = res.getIdentifier(baseName + "_night", "color", pkg);
        int nightAttr = res.getIdentifier("mlui_state_night", "attr", pkg);
        if (dayId == 0 || nightId == 0 || nightAttr == 0) {
            return null;
        }
        try {
            int dayColor = ContextCompat.getColor(ctx, dayId);
            int nightColor = ContextCompat.getColor(ctx, nightId);
            int[][] states = new int[][]{
                    new int[]{nightAttr},
                    new int[]{-nightAttr},
                    new int[]{}
            };
            int[] colors = new int[]{nightColor, dayColor, dayColor};
            ColorStateList csl = new ColorStateList(states, colors);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.RECTANGLE);
            g.setColor(csl);
            return g;
        } catch (Throwable t) {
            Log.d(TAG, "buildDayNightDrawable failed for " + baseName + ": " + t.getMessage());
            return null;
        }
    }

    @Override
    public boolean shouldAutoAddChildView() {
        return true;
    }
}
