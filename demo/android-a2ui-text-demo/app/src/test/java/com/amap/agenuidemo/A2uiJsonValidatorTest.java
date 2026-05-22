package com.amap.agenuidemo;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class A2uiJsonValidatorTest {

    private String validCreateSurface;
    private String validUpdateComponents;
    private String validUpdateDataModel;

    @Before
    public void setUp() throws Exception {
        validCreateSurface = new JSONObject()
                .put("version", "v0.9")
                .put("createSurface", new JSONObject()
                        .put("surfaceId", "surf-1")
                        .put("catalogId", "https://a2ui.org/specification/v0_9/standard_catalog.json"))
                .toString();

        validUpdateComponents = new JSONObject()
                .put("version", "v0.9")
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", "surf-1")
                        .put("components", new JSONArray()
                                .put(new JSONObject()
                                        .put("id", "root")
                                        .put("component", "Column")
                                        .put("children", new JSONArray().put("txt1")))
                                .put(new JSONObject()
                                        .put("id", "txt1")
                                        .put("component", "Text")
                                        .put("text", "Hello"))))
                .toString();

        validUpdateDataModel = "{}";
    }

    // --- validateFromRawText ---

    @Test
    public void validateFromRawText_nullInput_returnsInvalid() {
        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validateFromRawText(null);
        assertFalse(vr.isValid());
        assertTrue(vr.getErrors().get(0).contains("null or empty"));
    }

    @Test
    public void validateFromRawText_emptyInput_returnsInvalid() {
        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validateFromRawText("");
        assertFalse(vr.isValid());
    }

    @Test
    public void validateFromRawText_invalidJson_returnsInvalid() {
        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validateFromRawText("not json");
        assertFalse(vr.isValid());
        assertTrue(vr.getErrors().get(0).contains("normalize raw text"));
    }

    @Test
    public void validateFromRawText_tooFewMessages_returnsInvalid() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject(validCreateSurface));
        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validateFromRawText(arr.toString());
        assertFalse(vr.isValid());
        assertTrue(vr.getErrors().get(0).contains("missing updateComponents"));
    }

    @Test
    public void validateFromRawText_validInput_returnsValid() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject(validCreateSurface));
        arr.put(new JSONObject(validUpdateComponents));
        arr.put(new JSONObject(validUpdateDataModel));

        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validateFromRawText(arr.toString());
        assertTrue(vr.getFormattedReport(), vr.isValid());
    }

    @Test
    public void validateFromRawText_withMarkdownFences_stripsAndValidates() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject(validCreateSurface));
        arr.put(new JSONObject(validUpdateComponents));
        arr.put(new JSONObject(validUpdateDataModel));

        String fenced = "```json\n" + arr.toString() + "\n```";
        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validateFromRawText(fenced);
        assertTrue(vr.getFormattedReport(), vr.isValid());
    }

    @Test
    public void validateFromRawText_twoMessages_only_fillsThirdAsEmpty() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject(validCreateSurface));
        arr.put(new JSONObject(validUpdateComponents));

        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validateFromRawText(arr.toString());
        assertTrue(vr.getFormattedReport(), vr.isValid());
    }

    @Test
    public void validateFromRawText_strippedArrayWrapper_returnsValid() throws Exception {
        // Simulates text after SSE JsonArrayStripper removes [ ] and commas
        // e.g. "{obj1}\n{obj2}\n{obj3}" instead of "[{obj1},{obj2},{obj3}]"
        JSONObject cs = new JSONObject(validCreateSurface);
        JSONObject uc = new JSONObject(validUpdateComponents);
        String strippedText = cs.toString() + "\n" + uc.toString() + "\n{}";

        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validateFromRawText(strippedText);
        assertTrue(vr.getFormattedReport(), vr.isValid());
    }

    // --- validate (direct) ---

    @Test
    public void validate_validMessages_returnsValid() {
        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validate(
                validCreateSurface, validUpdateComponents, validUpdateDataModel);
        assertTrue(vr.getFormattedReport(), vr.isValid());
    }

    @Test
    public void validate_surfaceIdMismatch_returnsInvalid() throws Exception {
        String badCreate = new JSONObject()
                .put("version", "v0.9")
                .put("createSurface", new JSONObject()
                        .put("surfaceId", "surf-1")
                        .put("catalogId", "catalog"))
                .toString();

        String badUpdate = new JSONObject()
                .put("version", "v0.9")
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", "surf-2")
                        .put("components", new JSONArray()
                                .put(new JSONObject().put("id", "root").put("component", "Text").put("text", "hi"))))
                .toString();

        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validate(badCreate, badUpdate, "{}");
        assertFalse(vr.isValid());
        assertTrue(vr.getErrors().stream().anyMatch(e -> e.contains("mismatch")));
    }

    @Test
    public void validate_missingCreateSurfaceKey_returnsInvalid() throws Exception {
        String badCreate = new JSONObject().put("version", "v0.9").toString();
        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validate(badCreate, validUpdateComponents, "{}");
        assertFalse(vr.isValid());
        assertTrue(vr.getErrors().stream().anyMatch(e -> e.contains("createSurface")));
    }

    @Test
    public void validate_unknownComponentType_returnsInvalid() throws Exception {
        String badUpdate = new JSONObject()
                .put("version", "v0.9")
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", "surf-1")
                        .put("components", new JSONArray()
                                .put(new JSONObject().put("id", "root").put("component", "FakeWidget"))))
                .toString();

        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validate(validCreateSurface, badUpdate, "{}");
        assertFalse(vr.isValid());
        assertTrue(vr.getErrors().stream().anyMatch(e -> e.contains("unknown type")));
    }

    @Test
    public void validate_duplicateComponentId_returnsInvalid() throws Exception {
        String badUpdate = new JSONObject()
                .put("version", "v0.9")
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", "surf-1")
                        .put("components", new JSONArray()
                                .put(new JSONObject().put("id", "dup").put("component", "Text").put("text", "a"))
                                .put(new JSONObject().put("id", "dup").put("component", "Text").put("text", "b"))))
                .toString();

        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validate(validCreateSurface, badUpdate, "{}");
        assertFalse(vr.isValid());
        assertTrue(vr.getErrors().stream().anyMatch(e -> e.contains("Duplicate")));
    }

    @Test
    public void validate_missingComponentId_returnsInvalid() throws Exception {
        String badUpdate = new JSONObject()
                .put("version", "v0.9")
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", "surf-1")
                        .put("components", new JSONArray()
                                .put(new JSONObject().put("component", "Text").put("text", "hi"))))
                .toString();

        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validate(validCreateSurface, badUpdate, "{}");
        assertFalse(vr.isValid());
        assertTrue(vr.getErrors().stream().anyMatch(e -> e.contains("missing 'id'")));
    }

    @Test
    public void validate_eventAction_returnsInvalid() throws Exception {
        String badUpdate = new JSONObject()
                .put("version", "v0.9")
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", "surf-1")
                        .put("components", new JSONArray()
                                .put(new JSONObject()
                                        .put("id", "root")
                                        .put("component", "Button")
                                        .put("text", "click")
                                        .put("action", new JSONObject().put("event", new JSONObject())))))
                .toString();

        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validate(validCreateSurface, badUpdate, "{}");
        assertFalse(vr.isValid());
        assertTrue(vr.getErrors().stream().anyMatch(e -> e.contains("'event' action")));
    }

    @Test
    public void validate_functionCallNotInAllowlist_returnsInvalid() throws Exception {
        String badUpdate = new JSONObject()
                .put("version", "v0.9")
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", "surf-1")
                        .put("components", new JSONArray()
                                .put(new JSONObject()
                                        .put("id", "root")
                                        .put("component", "Button")
                                        .put("text", "click")
                                        .put("action", new JSONObject()
                                                .put("functionCall", new JSONObject()
                                                        .put("call", "deleteAll"))))))
                .toString();

        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validate(validCreateSurface, badUpdate, "{}");
        assertFalse(vr.isValid());
        assertTrue(vr.getErrors().stream().anyMatch(e -> e.contains("not in allowlist")));
    }

    @Test
    public void validate_toastFunctionCall_isValid() throws Exception {
        String goodUpdate = new JSONObject()
                .put("version", "v0.9")
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", "surf-1")
                        .put("components", new JSONArray()
                                .put(new JSONObject()
                                        .put("id", "root")
                                        .put("component", "Button")
                                        .put("text", "click")
                                        .put("action", new JSONObject()
                                                .put("functionCall", new JSONObject()
                                                        .put("call", "toast")
                                                        .put("args", new JSONObject().put("value", "hi")))))))
                .toString();

        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validate(validCreateSurface, goodUpdate, "{}");
        assertTrue(vr.getFormattedReport(), vr.isValid());
    }

    @Test
    public void validate_childReferenceNotFound_returnsInvalid() throws Exception {
        String badUpdate = new JSONObject()
                .put("version", "v0.9")
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", "surf-1")
                        .put("components", new JSONArray()
                                .put(new JSONObject()
                                        .put("id", "root")
                                        .put("component", "Column")
                                        .put("children", new JSONArray().put("missing-child")))))
                .toString();

        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validate(validCreateSurface, badUpdate, "{}");
        assertFalse(vr.isValid());
        assertTrue(vr.getErrors().stream().anyMatch(e -> e.contains("does not exist")));
    }

    @Test
    public void validate_multipleRootComponents_returnsInvalid() throws Exception {
        String badUpdate = new JSONObject()
                .put("version", "v0.9")
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", "surf-1")
                        .put("components", new JSONArray()
                                .put(new JSONObject().put("id", "a").put("component", "Text").put("text", "a"))
                                .put(new JSONObject().put("id", "b").put("component", "Text").put("text", "b"))))
                .toString();

        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validate(validCreateSurface, badUpdate, "{}");
        assertFalse(vr.isValid());
        assertTrue(vr.getErrors().stream().anyMatch(e -> e.contains("Multiple root")));
    }

    @Test
    public void validate_rootNotNamedRoot_returnsWarning() throws Exception {
        String update = new JSONObject()
                .put("version", "v0.9")
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", "surf-1")
                        .put("components", new JSONArray()
                                .put(new JSONObject().put("id", "myRoot").put("component", "Text").put("text", "hi"))))
                .toString();

        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validate(validCreateSurface, update, "{}");
        assertTrue(vr.isValid());
        assertTrue(vr.getWarnings().stream().anyMatch(w -> w.contains("recommended")));
    }

    @Test
    public void validate_formattedReport_containsErrors() throws Exception {
        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validate(null, null, null);
        assertFalse(vr.isValid());
        String report = vr.getFormattedReport();
        assertFalse(report.isEmpty());
        assertTrue(report.contains("ERRORS"));
    }

    @Test
    public void validate_missingVersionInCreateSurface_returnsInvalid() throws Exception {
        String badCreate = new JSONObject()
                .put("createSurface", new JSONObject()
                        .put("surfaceId", "surf-1")
                        .put("catalogId", "catalog"))
                .toString();
        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validate(badCreate, validUpdateComponents, "{}");
        assertFalse(vr.isValid());
        assertTrue(vr.getErrors().stream().anyMatch(e -> e.contains("version") && e.contains("createSurface")));
    }

    @Test
    public void validate_missingVersionInUpdateComponents_returnsInvalid() throws Exception {
        String badUpdate = new JSONObject()
                .put("updateComponents", new JSONObject()
                        .put("surfaceId", "surf-1")
                        .put("components", new JSONArray()
                                .put(new JSONObject().put("id", "root").put("component", "Text").put("text", "hi"))))
                .toString();
        A2uiJsonValidator.ValidationResult vr = A2uiJsonValidator.validate(validCreateSurface, badUpdate, "{}");
        assertFalse(vr.isValid());
        assertTrue(vr.getErrors().stream().anyMatch(e -> e.contains("version") && e.contains("updateComponents")));
    }
}
