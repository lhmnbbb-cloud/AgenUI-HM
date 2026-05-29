package com.amap.agenuidemo.commonui;

import android.content.Context;
import android.util.Log;

import com.amap.agenui.AGenUI;
import com.amap.agenui.render.surface.ThemeException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads the demo-side common-controls theme JSON (introduces mlui* Text variants)
 * into the AGenUI engine.
 *
 * <p>The theme JSON adds a private style key {@code melo-text-appearance} that
 * {@link MeloTextComponent} interprets at render time. Standard Java components
 * ignore the key, so it is safe to load even when the proprietary AAR is absent.
 *
 * <p>The accompanying design-token payload is intentionally minimal — the SDK's
 * {@code TokenParser} requires a {@code designTokens} root object but tolerates
 * an empty value. We do not redefine any default tokens here.
 */
public final class CommonControlsThemeLoader {

    private static final String TAG = "CommonControlsTheme";
    private static final String THEME_ASSET = "common_controls_theme.json";
    private static final String MIN_DESIGN_TOKEN = "{\"designTokens\":{}}";

    public interface Logger {
        void log(String message);
    }

    private CommonControlsThemeLoader() {}

    /**
     * Loads {@link #THEME_ASSET} and registers it as the default theme.
     *
     * @return true on success, false on any failure (already logged).
     */
    public static boolean loadIfPresent(AGenUI aGenUI, Context context, Logger logger) {
        if (aGenUI == null || context == null) {
            return false;
        }
        String themeJson = readAsset(context, THEME_ASSET);
        if (themeJson == null) {
            log(logger, "Theme asset not found: " + THEME_ASSET);
            return false;
        }
        try {
            aGenUI.registerDefaultTheme(themeJson, MIN_DESIGN_TOKEN);
            log(logger, "Common-controls theme registered (mlui* Text variants)");
            return true;
        } catch (ThemeException e) {
            Log.w(TAG, "registerDefaultTheme failed", e);
            log(logger, "Theme register failed: " + e.getMessage());
            return false;
        } catch (Throwable t) {
            Log.w(TAG, "registerDefaultTheme threw", t);
            log(logger, "Theme register threw: " + t.getMessage());
            return false;
        }
    }

    private static String readAsset(Context context, String name) {
        try (InputStream in = context.getAssets().open(name);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            Log.d(TAG, "Asset not readable: " + name + " (" + e.getMessage() + ")");
            return null;
        }
    }

    private static void log(Logger logger, String message) {
        Log.i(TAG, message);
        if (logger != null) {
            logger.log(message);
        }
    }
}
