package com.amap.agenuidemo.commonui;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Shows a toast via the company's MeloToast widget when the proprietary AAR is
 * on the classpath, falling back to {@link android.widget.Toast} otherwise.
 *
 * <p>Motivation: on the customized ROM the standard
 * {@code android.widget.Toast} is silently suppressed — the only reliable way
 * to surface user-facing toasts on real devices is the MeloToast widget.
 *
 * <p>Reflection only — no compile-time dependency on {@code com.meloui.*}.
 *
 * <p><b>API 29 compat</b>: the original
 * {@code MeloToast.makeText(Context, CharSequence, int)} entry point routes
 * through a 3-arg constructor that calls {@code Context.getDisplayId()} (API
 * 30+) and crashes on lower-API devices with {@code NoSuchMethodError}. The
 * SDK now exposes a 4-arg overload
 * {@code MeloToast.makeText(Context, CharSequence, int, int)} whose
 * constructor takes {@code displayId} as a parameter and avoids the API 30+
 * call. This helper targets that overload exclusively and hard-codes
 * {@code displayId = 0} (primary display).
 *
 * <p>{@code duration} is forwarded as {@code android.widget.Toast.LENGTH_SHORT}
 * / {@code LENGTH_LONG} (0 / 1) — same encoding used by MeloToast.
 */
public final class MeloToastHelper {

    private static final String TAG = "MeloToastHelper";
    private static final String MELO_TOAST_CLASS = "com.meloui.car.ui.widget.Toast";

    private static final int DEFAULT_DISPLAY_ID = 0;

    private static volatile Boolean sAvailable = null;
    private static volatile Method sMakeText = null; // Toast.makeText(Context, CharSequence, int, int)
    private static volatile Method sShow = null;     // Toast.show()

    private MeloToastHelper() {}

    /** True when the MeloToast widget is reachable via reflection. */
    public static boolean isAvailable() {
        ensureResolved();
        return Boolean.TRUE.equals(sAvailable);
    }

    /** Convenience: same shape as {@code Toast.makeText(ctx, text, duration).show()}. */
    public static void show(Context context, CharSequence text, int duration) {
        if (context == null) return;
        ensureResolved();
        if (Boolean.TRUE.equals(sAvailable) && sMakeText != null && sShow != null) {
            try {
                Object meloToast = sMakeText.invoke(null, context, text, duration, DEFAULT_DISPLAY_ID);
                if (meloToast != null) {
                    sShow.invoke(meloToast);
                    return;
                }
                Log.w(TAG, "MeloToast.makeText returned null — "
                        + "disabling MeloToast for this process");
                sAvailable = false;
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                Log.w(TAG, "MeloToast invocation threw " + (cause != null
                        ? cause.getClass().getName() + ": " + cause.getMessage()
                        : "<null cause>") + " — disabling MeloToast for this process",
                        cause);
                sAvailable = false;
            } catch (Throwable t) {
                Log.w(TAG, "MeloToast invocation failed (" + t.getClass().getSimpleName()
                        + "): " + t.getMessage() + " — disabling MeloToast for this process",
                        t);
                sAvailable = false;
            }
        }
        Toast.makeText(context, text, duration).show();
    }

    private static void ensureResolved() {
        if (sAvailable != null) return;
        synchronized (MeloToastHelper.class) {
            if (sAvailable != null) return;
            try {
                Class<?> meloToast = Class.forName(MELO_TOAST_CLASS);
                sMakeText = meloToast.getMethod(
                        "makeText",
                        Context.class, CharSequence.class, int.class, int.class);
                sShow = meloToast.getMethod("show");
                sAvailable = true;
                Log.i(TAG, "MeloToast available — using makeText(ctx, text, dur, displayId=0)");
            } catch (ClassNotFoundException e) {
                sAvailable = false;
                Log.i(TAG, "MeloToast not on classpath, using android.widget.Toast");
            } catch (NoSuchMethodException e) {
                sAvailable = false;
                Log.d(TAG, "MeloToast missing expected 4-arg makeText overload, "
                        + "using android.widget.Toast: " + e.getMessage());
            } catch (Throwable t) {
                sAvailable = false;
                Log.d(TAG, "MeloToast resolution failed, using android.widget.Toast: "
                        + t.getMessage());
            }
        }
    }
}
