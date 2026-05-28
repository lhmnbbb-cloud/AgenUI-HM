package com.amap.agenuidemo.commonui;

import android.content.Context;
import android.util.Log;

import com.amap.agenui.AGenUI;

/**
 * Detects the Melo/Gua common controls SDK and registers A2UI component
 * overrides so standard A2UI component types render with company-branded
 * widgets.
 *
 * <p>All registration is done via {@code aGenUI.registerComponent()}. If the
 * proprietary AAR is not on the classpath, this is a no-op and A2UI continues
 * rendering with the built-in standard Views.
 */
public final class CommonControlsRegistry {

    private static final String TAG = "CommonControlsRegistry";

    private CommonControlsRegistry() {}

    /**
     * Called once after AGenUI initialization. Checks whether the Melo/Gua
     * SDK is available and, if so, overrides the standard component factories
     * for Text, Card, and Button.
     *
     * <p>Row and Column are intentionally NOT overridden in this POC phase.
     * Replacing FlexContainerLayout with MeloLinearLayout would break stretch,
     * gap, flex-wrap, and other Flexbox semantics used by card templates.
     *
     * @param aGenUI  initialized AGenUI instance
     * @param context Android context
     */
    public static void registerIfAvailable(AGenUI aGenUI, Context context) {
        boolean available = CommonControlsViewFactory.isAvailable();
        Log.i(TAG, "Common controls available: " + available);

        if (!available) {
            return;
        }

        // Text → MeloTextView
        aGenUI.registerComponent("Text", new MeloTextComponentFactory());
        Log.i(TAG, "Registered common control component: Text → MeloTextView");

        // Card → MeloCardView (or MeloFrameLayout fallback)
        aGenUI.registerComponent("Card", new MeloCardComponentFactory());
        Log.i(TAG, "Registered common control component: Card → MeloCardView");

        // Button → MeloFrameLayout container (not MeloButton, see MeloButtonComponent doc)
        aGenUI.registerComponent("Button", new MeloButtonComponentFactory());
        Log.i(TAG, "Registered common control component: Button → MeloFrameLayout container");

        // Row/Column intentionally not overridden — see class Javadoc above
        Log.i(TAG, "Row/Column retained as native FlexContainerLayout (Flexbox semantics)");
    }
}
