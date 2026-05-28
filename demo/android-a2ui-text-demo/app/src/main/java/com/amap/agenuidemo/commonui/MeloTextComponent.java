package com.amap.agenuidemo.commonui;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.style.StyleHelper;

import java.util.Map;

/**
 * A2UI Text component backed by a MeloTextView (or standard TextView fallback).
 *
 * <p>Supports the same properties as the standard TextComponent:
 * text.literalString, textChunk, and styles (font-size, color, font-weight,
 * line-clamp, text-align). Reuses {@link StyleHelper#applyTextStyles} since
 * MeloTextView extends AppCompatTextView.
 */
public class MeloTextComponent extends A2UIComponent {

    private static final String TAG = "MeloTextComponent";

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
                    StyleHelper.applyTextStyles(textView, styles, context);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply styles", e);
            }
        }
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
