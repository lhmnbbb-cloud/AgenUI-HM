package com.amap.agenuidemo.commonui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;

import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.component.A2UILayoutComponent;
import com.amap.agenui.render.style.StyleHelper;

import java.util.Map;

/**
 * A2UI Card component backed by a {@code MeloFrameLayout} (or
 * {@link android.widget.FrameLayout} when the proprietary AAR is absent).
 *
 * <p>Historically this component used the AndroidX card container widget.
 * That widget turned out to be a poor match for A2UI Card semantics:
 * <ul>
 *   <li>Its {@code setPadding} silently no-ops on the content area; only the
 *       dedicated content-padding API sizes the inner box.</li>
 *   <li>Its built-in background colour, corner radius and elevation compete
 *       with the {@link GradientDrawable} we install for
 *       {@code melo-card-background}, requiring extra reset code.</li>
 *   <li>Its compat-padding and corner-overlap insets introduce surprises that
 *       fight Flexbox parents.</li>
 * </ul>
 * FrameLayout + GradientDrawable + plain {@code setPadding} is a cleaner fit:
 * the Mlui card background becomes a single drawable, and padding behaves
 * exactly like every other A2UI container.
 */
public class MeloCardComponent extends A2UILayoutComponent {

    private static final String TAG = "MeloCardComponent";
    private static final String STYLE_KEY_BACKGROUND = "melo-card-background";
    private static final String STYLE_KEY_RADIUS = "melo-card-radius";
    private static final String DEFAULT_RADIUS = "16px";

    private ViewGroup cardView;
    private A2UIComponent childComponent;

    public MeloCardComponent(String id, Map<String, Object> properties) {
        super(id, "Card");
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    @Override
    public View createView(Context context, ViewGroup parent) {
        View view = super.createView(context, parent);
        applyCardAppearanceIfPresent();
        applyCardPadding();
        return view;
    }

    @Override
    protected View onCreateView(Context context) {
        cardView = CommonControlsViewFactory.createCardView(context);
        cardView.setLayoutParams(new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cardView.setClickable(true);
        cardView.setFocusable(true);
        return cardView;
    }

    @Override
    public void onUpdateProperties(Map<String, Object> properties) {
        super.onUpdateProperties(properties);
        applyCardAppearanceIfPresent();
        applyCardPadding();
    }

    /**
     * Resolves the demo-private {@code melo-card-background} (color resource
     * name) and {@code melo-card-radius} (dimension) keys to a
     * {@link GradientDrawable} and installs it as the card background.
     *
     * <p>Runs after {@code super.onUpdateProperties} so it overrides whatever
     * StyleHelper.applyBackground / applyBorder may have written for the
     * fallback {@code background-color / border-radius} declared on the Card
     * variant in {@code common_controls_theme.json}.
     *
     * <p>Background resolution order — important: ColorStateList first so the
     * day/night-aware {@code mlui_app_bg_color_*} selectors keep their state
     * switching. Flattening to a single color via {@code getColor()} would lock
     * the card to its day value.
     * <ol>
     *   <li>{@link ContextCompat#getColorStateList(Context, int)} —
     *       preserves day/night selectors.</li>
     *   <li>{@link ContextCompat#getColor(Context, int)} — flat color
     *       fallback if the resource is a plain {@code <color>}.</li>
     *   <li>{@link ContextCompat#getDrawable(Context, int)} — last resort for
     *       drawable resources.</li>
     * </ol>
     */
    private void applyCardAppearanceIfPresent() {
        if (cardView == null) {
            return;
        }
        Map<String, Object> styles = extractStyles(this.properties);
        if (styles == null || !styles.containsKey(STYLE_KEY_BACKGROUND)) {
            return;
        }
        Object rawName = styles.get(STYLE_KEY_BACKGROUND);
        String resourceName = rawName == null ? "" : String.valueOf(rawName).trim();
        if (resourceName.isEmpty()) {
            return;
        }

        Context ctx = cardView.getContext();
        Object rawRadius = styles.containsKey(STYLE_KEY_RADIUS)
                ? styles.get(STYLE_KEY_RADIUS) : DEFAULT_RADIUS;
        int radiusPx = StyleHelper.parseDimension(rawRadius, ctx);
        if (radiusPx < 0) {
            radiusPx = 0;
        }

        GradientDrawable gradient = buildBackgroundDrawable(ctx, resourceName);
        if (gradient == null) {
            Log.w(TAG, "melo-card-background not resolvable; leaving fallback decoration: "
                    + resourceName);
            return;
        }
        gradient.setCornerRadius(radiusPx);
        cardView.setBackground(gradient);
    }

    /**
     * Builds a {@link GradientDrawable} carrying the color for {@code name}.
     * Always returns a RECTANGLE; the caller layers the corner radius on top.
     * Returns {@code null} only when none of the three resolution strategies
     * succeed.
     */
    private static GradientDrawable buildBackgroundDrawable(Context ctx, String name) {
        if (ctx == null) return null;
        Resources res = ctx.getResources();
        String pkg = ctx.getPackageName();

        int colorId = res.getIdentifier(name, "color", pkg);
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
                Log.d(TAG, "ColorStateList lookup failed for " + name + ": " + t.getMessage());
            }
            try {
                int color = ContextCompat.getColor(ctx, colorId);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.RECTANGLE);
                g.setColor(color);
                return g;
            } catch (Throwable t) {
                Log.d(TAG, "ContextCompat.getColor failed for " + name + ": " + t.getMessage());
            }
        }

        int drawableId = res.getIdentifier(name, "drawable", pkg);
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
                Log.d(TAG, "Drawable for " + name
                        + " is not a flat/gradient color; skipping radius wrap");
            } catch (Throwable t) {
                Log.d(TAG, "ContextCompat.getDrawable failed for " + name + ": " + t.getMessage());
            }
        }

        return null;
    }

    /**
     * Mirrors A2UI padding shorthand to {@link View#setPadding} on the backing
     * FrameLayout. Now that the Card backing view is a plain ViewGroup, this is
     * a regular padding apply — no more legacy content-padding special case.
     */
    private void applyCardPadding() {
        if (cardView == null) {
            return;
        }
        Map<String, Object> styles = extractStyles(this.properties);
        if (!hasPaddingStyle(styles)) {
            return;
        }
        Context context = cardView.getContext();

        int paddingLeft = cardView.getPaddingLeft();
        int paddingTop = cardView.getPaddingTop();
        int paddingRight = cardView.getPaddingRight();
        int paddingBottom = cardView.getPaddingBottom();

        if (styles.containsKey("padding")) {
            int[] paddings = parseSpacingValues(styles.get("padding"), context);
            paddingTop = paddings[0];
            paddingRight = paddings[1];
            paddingBottom = paddings[2];
            paddingLeft = paddings[3];
        }

        if (styles.containsKey("padding-inline-start")) {
            paddingLeft = StyleHelper.parseDimension(styles.get("padding-inline-start"), context);
        } else if (styles.containsKey("padding-left")) {
            paddingLeft = StyleHelper.parseDimension(styles.get("padding-left"), context);
        }

        if (styles.containsKey("padding-inline-end")) {
            paddingRight = StyleHelper.parseDimension(styles.get("padding-inline-end"), context);
        } else if (styles.containsKey("padding-right")) {
            paddingRight = StyleHelper.parseDimension(styles.get("padding-right"), context);
        }

        if (styles.containsKey("padding-block-start")) {
            paddingTop = StyleHelper.parseDimension(styles.get("padding-block-start"), context);
        } else if (styles.containsKey("padding-top")) {
            paddingTop = StyleHelper.parseDimension(styles.get("padding-top"), context);
        }

        if (styles.containsKey("padding-block-end")) {
            paddingBottom = StyleHelper.parseDimension(styles.get("padding-block-end"), context);
        } else if (styles.containsKey("padding-bottom")) {
            paddingBottom = StyleHelper.parseDimension(styles.get("padding-bottom"), context);
        }

        cardView.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
    }

    private boolean hasPaddingStyle(Map<String, Object> styles) {
        return styles != null && (
                styles.containsKey("padding")
                        || styles.containsKey("padding-inline-start")
                        || styles.containsKey("padding-inline-end")
                        || styles.containsKey("padding-block-start")
                        || styles.containsKey("padding-block-end")
                        || styles.containsKey("padding-left")
                        || styles.containsKey("padding-right")
                        || styles.containsKey("padding-top")
                        || styles.containsKey("padding-bottom"));
    }

    /** CSS spacing shorthand parser. Return order: top, right, bottom, left. */
    private int[] parseSpacingValues(Object value, Context context) {
        if (value == null) {
            return new int[]{0, 0, 0, 0};
        }

        String[] parts = String.valueOf(value).trim().split("\\s+");
        int[] result = new int[4];

        switch (parts.length) {
            case 1:
                int all = StyleHelper.parseDimension(parts[0], context);
                result[0] = result[1] = result[2] = result[3] = all;
                break;
            case 2:
                int vertical = StyleHelper.parseDimension(parts[0], context);
                int horizontal = StyleHelper.parseDimension(parts[1], context);
                result[0] = result[2] = vertical;
                result[1] = result[3] = horizontal;
                break;
            case 3:
                result[0] = StyleHelper.parseDimension(parts[0], context);
                result[1] = result[3] = StyleHelper.parseDimension(parts[1], context);
                result[2] = StyleHelper.parseDimension(parts[2], context);
                break;
            case 4:
            default:
                result[0] = StyleHelper.parseDimension(parts[0], context);
                result[1] = StyleHelper.parseDimension(parts[1], context);
                result[2] = StyleHelper.parseDimension(parts[2], context);
                result[3] = StyleHelper.parseDimension(parts[3], context);
                break;
        }

        return result;
    }

    @Override
    public void addChild(A2UIComponent child) {
        if (childComponent != null && childComponent.getView() != null && cardView != null) {
            cardView.removeView(childComponent.getView());
        }
        childComponent = child;
        if (child != null && child.getView() != null && cardView != null) {
            View childView = child.getView();
            childView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            cardView.addView(childView);
        }
    }

    @Override
    public void removeChild(A2UIComponent child) {
        if (childComponent == child) {
            if (child != null && child.getView() != null && cardView != null) {
                cardView.removeView(child.getView());
            }
            childComponent = null;
        }
    }
}
