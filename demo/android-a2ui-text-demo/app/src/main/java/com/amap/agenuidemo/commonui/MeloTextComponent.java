package com.amap.agenuidemo.commonui;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.style.StyleHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * A2UI Text component backed by a MeloTextView (or standard TextView fallback).
 *
 * <p>Supports the same properties as the standard TextComponent:
 * text.literalString, textChunk, and styles (font-size, color, font-weight,
 * line-clamp, text-align). Reuses {@link StyleHelper#applyTextStyles} since
 * MeloTextView extends AppCompatTextView.
 *
 * <p>Additionally recognises the demo-side private style key
 * {@code melo-text-appearance} (set by mlui* Text variants declared in
 * {@code assets/common_controls_theme.json}). When present, the value is
 * resolved to a style resource via {@link Resources#getIdentifier} and applied
 * via {@code setTextAppearance} before the regular style pipeline runs, so any
 * explicit {@code text-align}/{@code color} the template carries can still
 * override the appearance.
 */
public class MeloTextComponent extends A2UIComponent {

    private static final String TAG = "MeloTextComponent";
    private static final String STYLE_KEY_APPEARANCE = "melo-text-appearance";

    private Context context;
    private TextView textView;
    private StringBuilder currentText = new StringBuilder();

    public MeloTextComponent(Context context, String id, Map<String, Object> properties) {
        super(id, "Text");
        this.context = context;
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    @Override
    protected View onCreateView(Context context) {
        if (this.context == null) {
            this.context = context;
        }
        textView = CommonControlsViewFactory.createTextView(context);
        textView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        onUpdateProperties(this.properties);
        return textView;
    }

    @Override
    protected void onUpdateProperties(Map<String, Object> properties) {
        if (textView == null) return;

        if (properties.containsKey("text")) {
            Object textValue = properties.get("text");
            String text = extractTextValue(textValue);
            currentText = new StringBuilder(text);
            textView.setText(currentText.toString());
        } else if (properties.containsKey("textChunk")) {
            Object textValue = properties.get("textChunk");
            String textChunk = extractTextValue(textValue);
            if (textChunk != null) {
                currentText.append(textChunk);
                textView.setText(currentText.toString());
            }
        }

        if (properties.containsKey("styles")) {
            try {
                Object stylesValue = properties.get("styles");
                if (stylesValue instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> styles = (Map<String, Object>) stylesValue;

                    // Apply melo-text-appearance first so any subsequent explicit
                    // styles (text-align, color overrides) win.
                    Map<String, Object> stylesForHelper = applyTextAppearanceIfPresent(styles);
                    StyleHelper.applyTextStyles(textView, stylesForHelper, context);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply styles", e);
            }
        }
    }

    /**
     * Resolves and applies the {@code melo-text-appearance} style if present.
     * Returns a styles map suitable for {@link StyleHelper#applyTextStyles} —
     * either the original map (when the key is absent) or a defensive copy
     * with the appearance key removed so StyleHelper does not see it.
     */
    private Map<String, Object> applyTextAppearanceIfPresent(Map<String, Object> styles) {
        if (styles == null || !styles.containsKey(STYLE_KEY_APPEARANCE)) {
            return styles;
        }
        Object raw = styles.get(STYLE_KEY_APPEARANCE);
        String appearance = raw == null ? "" : String.valueOf(raw).trim();
        if (!appearance.isEmpty()) {
            applyTextAppearance(appearance);
        }
        Map<String, Object> copy = new HashMap<>(styles);
        copy.remove(STYLE_KEY_APPEARANCE);
        return copy;
    }

    private void applyTextAppearance(String styleName) {
        Context ctx = textView != null ? textView.getContext() : context;
        if (ctx == null) {
            Log.w(TAG, "No context for setTextAppearance: " + styleName);
            return;
        }
        int styleId = resolveStyleId(ctx, styleName);
        if (styleId == 0) {
            Log.w(TAG, "TextAppearance style not found: " + styleName
                    + " (ignored; proprietary AAR likely absent)");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                textView.setTextAppearance(styleId);
            } else {
                //noinspection deprecation
                textView.setTextAppearance(ctx, styleId);
            }
        } catch (Exception e) {
            Log.w(TAG, "setTextAppearance(" + styleName + ") failed: " + e.getMessage());
        }
    }

    /**
     * Looks up a style resource id by name, accepting both dot-style
     * ({@code MluiTextAppearance.Title.Large}) and underscore-style
     * ({@code MluiTextAppearance_Title_Large}) identifiers.
     */
    private static int resolveStyleId(Context ctx, String styleName) {
        Resources res = ctx.getResources();
        String pkg = ctx.getPackageName();
        int id = res.getIdentifier(styleName, "style", pkg);
        if (id != 0) return id;
        String underscored = styleName.replace('.', '_');
        if (!underscored.equals(styleName)) {
            id = res.getIdentifier(underscored, "style", pkg);
        }
        return id;
    }

    private String extractTextValue(Object textValue) {
        if (textValue instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> textMap = (Map<String, Object>) textValue;
            if (textMap.containsKey("literalString")) {
                return String.valueOf(textMap.get("literalString"));
            }
            if (textMap.containsKey("path")) {
                return String.valueOf(textMap.get("path"));
            }
        }
        return String.valueOf(textValue);
    }

    @Override
    protected void onDestroy() {
        currentText.setLength(0);
        textView = null;
    }
}
