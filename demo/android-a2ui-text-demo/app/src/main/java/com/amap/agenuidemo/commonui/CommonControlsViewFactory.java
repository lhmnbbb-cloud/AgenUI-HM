package com.amap.agenuidemo.commonui;

import android.content.Context;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Creates Android View instances, preferring Melo/Gua common controls when the
 * proprietary AAR is on the classpath. Uses reflection to avoid compile-time
 * dependency on {@code com.meloui.car.ui.*}. Falls back to standard Android
 * Views if the AAR is absent or reflection fails.
 */
public final class CommonControlsViewFactory {

    private static final String TAG = "CommonControlsViewFactory";
    private static final String PKG = "com.meloui.car.ui.widget";

    private static volatile Boolean sAvailable = null;

    private CommonControlsViewFactory() {}

    /** True if at least one Melo widget class can be loaded via reflection. */
    public static boolean isAvailable() {
        if (sAvailable == null) {
            try {
                Class.forName(PKG + ".MeloTextView");
                sAvailable = true;
                Log.i(TAG, "Common controls SDK detected (MeloTextView found)");
            } catch (ClassNotFoundException e) {
                sAvailable = false;
                Log.i(TAG, "Common controls SDK not found, using standard Android Views");
            }
        }
        return sAvailable;
    }

    // ---- TextView ----

    public static TextView createTextView(Context context) {
        try {
            Class<?> clz = Class.forName(PKG + ".MeloTextView");
            return (TextView) clz.getConstructor(Context.class).newInstance(context);
        } catch (Exception e) {
            Log.d(TAG, "Falling back to TextView: " + e.getMessage());
            return new TextView(context);
        }
    }

    // ---- Card container (ViewGroup) ----

    /**
     * A2UI Card backing view. Deliberately backed by MeloFrameLayout /
     * FrameLayout — never the AndroidX card container widget. That widget's
     * setPadding semantics (content vs frame padding) and built-in background/radius/elevation fight
     * the GradientDrawable we install for {@code melo-card-background}.
     * FrameLayout + GradientDrawable + plain setPadding is a cleaner fit.
     */
    public static android.view.ViewGroup createCardView(Context context) {
        try {
            Class<?> clz = Class.forName(PKG + ".MeloFrameLayout");
            android.view.ViewGroup view = (android.view.ViewGroup)
                    clz.getConstructor(Context.class).newInstance(context);
            Log.d(TAG, "Card backed by MeloFrameLayout");
            return view;
        } catch (Exception e) {
            Log.d(TAG, "Card falling back to FrameLayout (MeloFrameLayout unavailable): "
                    + e.getMessage());
            return new FrameLayout(context);
        }
    }

    // ---- FrameLayout ----

    public static FrameLayout createFrameLayout(Context context) {
        try {
            Class<?> clz = Class.forName(PKG + ".MeloFrameLayout");
            return (FrameLayout) clz.getConstructor(Context.class).newInstance(context);
        } catch (Exception e) {
            Log.d(TAG, "Falling back to FrameLayout: " + e.getMessage());
            return new FrameLayout(context);
        }
    }
}
