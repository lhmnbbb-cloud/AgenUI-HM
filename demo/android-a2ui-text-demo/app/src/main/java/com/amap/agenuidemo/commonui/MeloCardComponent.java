package com.amap.agenuidemo.commonui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.cardview.widget.CardView;

import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.component.A2UILayoutComponent;
import com.amap.agenui.render.style.StyleHelper;

import java.util.Map;

/**
 * A2UI Card component backed by a MeloCardView (or MeloFrameLayout/Fallback).
 *
 * <p>Same semantics as the standard CardComponent: a container with one child.
 * Uses {@link CommonControlsViewFactory#createCardView} for reflection-based
 * View creation with automatic fallback.
 *
 * <p>MeloCardView extends CardView, whose content spacing API is
 * {@code setContentPadding()} rather than {@code setPadding()}. This component
 * mirrors A2UI padding styles into CardView content padding so existing card
 * templates keep their inner spacing.
 */
public class MeloCardComponent extends A2UILayoutComponent {

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
        applyCardContentPadding();
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
        applyCardContentPadding();
    }

    private void applyCardContentPadding() {
        if (!(cardView instanceof CardView)) {
            return;
        }

        Map<String, Object> styles = extractStyles(this.properties);
        if (!hasPaddingStyle(styles)) {
            return;
        }

        CardView card = (CardView) cardView;
        Context context = card.getContext();

        int paddingLeft = card.getContentPaddingLeft();
        int paddingTop = card.getContentPaddingTop();
        int paddingRight = card.getContentPaddingRight();
        int paddingBottom = card.getContentPaddingBottom();

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

        card.setContentPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
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

    /**
     * Mirrors StyleHelper's CSS spacing shorthand parser for CardView content
     * padding. Return order: top, right, bottom, left.
     */
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
