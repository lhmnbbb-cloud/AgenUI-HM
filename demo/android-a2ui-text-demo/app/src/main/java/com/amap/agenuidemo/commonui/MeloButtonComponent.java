package com.amap.agenuidemo.commonui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.amap.agenui.render.component.A2UIComponent;
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
 * (background-color, etc.).
 */
public class MeloButtonComponent extends A2UILayoutComponent {

    private static final String TAG = "MeloButtonComponent";

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
    protected View onCreateView(Context context) {
        buttonContainer = CommonControlsViewFactory.createFrameLayout(context);
        buttonContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((FrameLayout.LayoutParams) buttonContainer.getLayoutParams()).gravity = Gravity.CENTER;

        if (!properties.isEmpty()) {
            applyProperties();
        }
        return buttonContainer;
    }

    @Override
    public void onUpdateProperties(Map<String, Object> properties) {
        if (buttonContainer == null) return;
        applyProperties();
    }

    private void applyProperties() {
        if (buttonContainer == null) return;

        if (properties.containsKey("child")) {
            childComponentId = String.valueOf(properties.get("child"));
        }

        if (properties.containsKey("checks")) {
            Object checksValue = properties.get("checks");
            if (checksValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> checksMap = (Map<String, Object>) checksValue;
                Object resultObj = checksMap.get("result");
                boolean result = resultObj instanceof Boolean ? (Boolean) resultObj : true;
                buttonContainer.setClickable(result);
                buttonContainer.setEnabled(result);
            }
        }

        boolean isDisabled = false;
        if (properties.containsKey("disable")) {
            Object disableValue = properties.get("disable");
            if (disableValue instanceof Boolean) {
                isDisabled = (Boolean) disableValue;
            }
        }
        buttonContainer.setClickable(!isDisabled);
        buttonContainer.setEnabled(!isDisabled);

        if (properties.containsKey("styles")) {
            Object stylesValue = properties.get("styles");
            if (stylesValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> styles = (Map<String, Object>) stylesValue;
                applyStyles(styles, isDisabled);
            }
        }
    }

    private void applyStyles(Map<String, Object> styles, boolean isDisabled) {
        if (styles == null || styles.isEmpty()) return;

        if (isDisabled) {
            buttonContainer.setAlpha(0.5f);
        } else {
            buttonContainer.setAlpha(1.0f);
            if (styles.containsKey("background-color")) {
                String colorStr = String.valueOf(styles.get("background-color"));
                int color = StyleHelper.parseColor(colorStr);
                if (color != 0) {
                    setContainerBackgroundColor(color);
                } else {
                    setContainerBackgroundColor(Color.TRANSPARENT);
                }
            } else {
                setContainerBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    private void setContainerBackgroundColor(int color) {
        Drawable drawable = buttonContainer.getBackground();
        if (drawable instanceof GradientDrawable) {
            ((GradientDrawable) drawable).setColor(color);
            buttonContainer.setBackground(drawable);
        }
    }

    @Override
    public boolean shouldAutoAddChildView() {
        return true;
    }
}
