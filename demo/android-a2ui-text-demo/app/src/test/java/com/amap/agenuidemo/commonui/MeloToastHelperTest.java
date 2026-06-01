package com.amap.agenuidemo.commonui;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Source-level guard for the MeloToast adapter.
 *
 * <p>Real-device verification on the customized ROM showed that standard
 * {@code android.widget.Toast} is silently suppressed — user-facing toasts
 * must go through {@code com.meloui.car.ui.widget.Toast}. {@link MeloToastHelper}
 * encapsulates the reflective bridge with an {@code android.widget.Toast}
 * fallback, so app-side callsites must NOT call {@code Toast.makeText(...).show()}
 * directly.
 *
 * <p>Robolectric isn't on the test classpath; we assert source contents
 * instead. Forbidden tokens are concatenated at runtime so this test's own
 * source stays clean.
 */
public class MeloToastHelperTest {

    private static final String HELPER_PATH =
            "src/main/java/com/amap/agenuidemo/commonui/MeloToastHelper.java";
    private static final String ACTIVITY_PATH =
            "src/main/java/com/amap/agenuidemo/TextDemoActivity.java";
    private static final String TOAST_FN_PATH =
            "src/main/java/com/amap/agenuidemo/ToastFunction.java";

    private static String readSource(String path) throws IOException {
        try (InputStream in = new FileInputStream(path);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    @Test
    public void helper_resolvesMeloToastReflectively() throws Exception {
        String src = readSource(HELPER_PATH);
        // Must reflect — no direct import of com.meloui.* (skill rule §3).
        assertFalse("MeloToastHelper must not import com.meloui.*",
                src.contains("import com.meloui."));
        // FQN string ensures the Class.forName target stays correct. The
        // 4-arg makeText overload is required: the 3-arg overload routes
        // through a constructor that calls Context.getDisplayId() (API 30+)
        // and crashes on API 29 devices. See helper Javadoc.
        assertTrue("MeloToastHelper must reference MeloToast FQN",
                src.contains("com.meloui.car.ui.widget.Toast"));
        assertTrue("MeloToastHelper must use Class.forName for resolution",
                src.contains("Class.forName"));
        // Guard the API-29-safe overload signature.
        assertTrue("MeloToastHelper must resolve the 4-arg makeText overload "
                        + "(3-arg overload triggers Context.getDisplayId() and crashes pre-API 30)",
                src.contains("\"makeText\"")
                        && src.contains("Context.class, CharSequence.class, int.class, int.class"));
    }

    @Test
    public void helper_fallsBackToAndroidToast() throws Exception {
        String src = readSource(HELPER_PATH);
        // Fallback path is what lets unbranded builds + reflection failures
        // still surface a toast. Don't let anyone remove it.
        assertTrue("MeloToastHelper must keep an android.widget.Toast fallback path",
                src.contains("android.widget.Toast")
                        && src.contains("Toast.makeText(context, text, duration).show()"));
    }

    @Test
    public void textDemoActivity_doesNotCallAndroidToastDirectly() throws Exception {
        String src = readSource(ACTIVITY_PATH);
        // Build the banned token at runtime so it doesn't appear literally in
        // this assertion's source.
        String banned = "Toast." + "makeText(";
        assertFalse("TextDemoActivity must use MeloToastHelper.show(...) instead of "
                        + "android.widget.Toast (custom ROM suppresses the platform toast)",
                src.contains(banned));
        assertTrue("TextDemoActivity must call MeloToastHelper.show",
                src.contains("MeloToastHelper.show("));
    }

    @Test
    public void toastFunction_doesNotCallAndroidToastDirectly() throws Exception {
        String src = readSource(TOAST_FN_PATH);
        String banned = "Toast." + "makeText(";
        assertFalse("ToastFunction must use MeloToastHelper.show(...) instead of "
                        + "android.widget.Toast",
                src.contains(banned));
        assertTrue("ToastFunction must route through MeloToastHelper",
                src.contains("MeloToastHelper.show("));
    }
}
