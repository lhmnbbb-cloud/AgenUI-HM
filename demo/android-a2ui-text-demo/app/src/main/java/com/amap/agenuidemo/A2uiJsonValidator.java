package com.amap.agenuidemo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class A2uiJsonValidator {

    private static final Set<String> VALID_COMPONENT_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "Text", "Image", "Icon", "Video", "AudioPlayer",
                    "Row", "Column", "List", "Card", "Tabs", "Modal", "Divider",
                    "Button", "TextField", "CheckBox", "ChoicePicker", "Slider", "DateTimeInput",
                    "RichText", "Table", "Web", "Carousel"
            ))
    );

    private static final Set<String> ALLOWED_FUNCTION_CALLS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("toast"))
    );

    public static ValidationResult validateFromRawText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return new ValidationResult(false,
                    Collections.singletonList("Raw text is null or empty"),
                    Collections.emptyList());
        }
        try {
            String[] messages = A2uiMessageNormalizer.normalizeRawText(rawText);
            return validate(messages[0], messages[1], messages[2]);
        } catch (Exception e) {
            return new ValidationResult(false,
                    Collections.singletonList("Failed to normalize raw text: " + e.getMessage()),
                    Collections.emptyList());
        }
    }

    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = Collections.unmodifiableList(errors);
            this.warnings = Collections.unmodifiableList(warnings);
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public String getFormattedReport() {
            StringBuilder sb = new StringBuilder();
            if (!errors.isEmpty()) {
                sb.append("ERRORS:\n");
                for (String e : errors) {
                    sb.append("  - ").append(e).append("\n");
                }
            }
            if (!warnings.isEmpty()) {
                sb.append("WARNINGS:\n");
                for (String w : warnings) {
                    sb.append("  - ").append(w).append("\n");
                }
            }
            return sb.toString().trim();
        }
    }

    public static ValidationResult validate(String createSurfaceJson,
                                            String updateComponentsJson,
                                            String updateDataModelJson) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String createSurfaceId = null;

        // 1. Validate createSurface
        JSONObject createSurface = parseJson(createSurfaceJson, "createSurface", errors);
        if (createSurface != null) {
            if (!createSurface.has("createSurface")) {
                errors.add("createSurface message missing top-level 'createSurface' key");
            } else {
                try {
                    JSONObject cs = createSurface.getJSONObject("createSurface");
                    if (!cs.has("surfaceId")) {
                        errors.add("createSurface.surfaceId is missing");
                    } else {
                        createSurfaceId = cs.getString("surfaceId");
                    }
                    if (!cs.has("catalogId")) {
                        errors.add("createSurface.catalogId is missing");
                    }
                } catch (Exception e) {
                    errors.add("createSurface value is not a JSON object: " + e.getMessage());
                }
            }
            if (!createSurface.has("version")) {
                errors.add("createSurface message missing 'version' key (SDK requires it for processing)");
            }
        }

        // 2. Validate updateComponents
        JSONObject updateComponents = parseJson(updateComponentsJson, "updateComponents", errors);
        if (updateComponents != null) {
            if (!updateComponents.has("updateComponents")) {
                errors.add("updateComponents message missing top-level 'updateComponents' key");
            } else {
                try {
                    JSONObject uc = updateComponents.getJSONObject("updateComponents");
                    if (!uc.has("surfaceId")) {
                        errors.add("updateComponents.surfaceId is missing");
                    } else if (createSurfaceId != null) {
                        String ucSurfaceId = uc.getString("surfaceId");
                        if (!createSurfaceId.equals(ucSurfaceId)) {
                            errors.add("surfaceId mismatch: createSurface has '" + createSurfaceId
                                    + "' but updateComponents has '" + ucSurfaceId + "'");
                        }
                    }
                    if (!uc.has("components")) {
                        errors.add("updateComponents.components is missing");
                    } else {
                        Object compsObj = uc.get("components");
                        if (compsObj instanceof JSONArray) {
                            validateComponentArray((JSONArray) compsObj, errors, warnings);
                        } else {
                            errors.add("updateComponents.components is not a JSON array");
                        }
                    }
                } catch (Exception e) {
                    errors.add("updateComponents value is not a JSON object: " + e.getMessage());
                }
            }
            if (!updateComponents.has("version")) {
                errors.add("updateComponents message missing 'version' key (SDK requires it for processing)");
            }
        }

        // 3. Validate updateDataModel (optional)
        if (updateDataModelJson != null && !updateDataModelJson.trim().equals("{}")) {
            JSONObject updateDataModel = parseJson(updateDataModelJson, "updateDataModel", errors);
            if (updateDataModel != null) {
                if (!updateDataModel.has("updateDataModel")) {
                    warnings.add("updateDataModel message has content but missing 'updateDataModel' key");
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private static JSONObject parseJson(String json, String label, List<String> errors) {
        if (json == null || json.trim().isEmpty()) {
            errors.add(label + " JSON is null or empty");
            return null;
        }
        try {
            return new JSONObject(json);
        } catch (Exception e) {
            errors.add(label + " JSON parse error: " + e.getMessage());
            return null;
        }
    }

    private static void validateComponentArray(JSONArray components,
                                               List<String> errors,
                                               List<String> warnings) {
        if (components.length() == 0) {
            warnings.add("updateComponents.components array is empty");
            return;
        }

        // Collect all component ids and detect duplicates
        Set<String> allIds = new HashSet<>();
        Set<String> duplicateIds = new HashSet<>();
        for (int i = 0; i < components.length(); i++) {
            try {
                JSONObject comp = components.getJSONObject(i);
                if (comp.has("id")) {
                    String id = comp.getString("id");
                    if (!allIds.add(id)) {
                        duplicateIds.add(id);
                    }
                }
            } catch (Exception e) {
                // handled below
            }
        }
        for (String dupId : duplicateIds) {
            errors.add("Duplicate component id: '" + dupId + "'");
        }

        // Collect ids referenced as children/child/parent
        Set<String> referencedChildIds = new HashSet<>();
        // Track which ids are containers with children/child
        Set<String> containerIds = new HashSet<>();

        for (int i = 0; i < components.length(); i++) {
            try {
                JSONObject comp = components.getJSONObject(i);
                String compId = comp.optString("id", "");

                if (!comp.has("id")) {
                    errors.add("Component at index " + i + " missing 'id' field");
                }
                String componentType = getComponentType(comp);
                if (componentType == null || componentType.isEmpty()) {
                    errors.add("Component at index " + i + " missing 'component' type field");
                } else {
                    if (!VALID_COMPONENT_TYPES.contains(componentType)) {
                        errors.add("Component '" + comp.optString("id", "#" + i)
                                + "' has unknown type '" + componentType + "' (not in catalog)");
                    }
                }

                // Check children references
                Object childrenValue = getComponentProperty(comp, "children");
                if (childrenValue != null) {
                    containerIds.add(compId);
                    try {
                        JSONArray children = (JSONArray) childrenValue;
                        for (int j = 0; j < children.length(); j++) {
                            referencedChildIds.add(children.getString(j));
                        }
                    } catch (Exception e) {
                        errors.add("Component '" + compId + "' has invalid 'children' array");
                    }
                }
                Object childValue = getComponentProperty(comp, "child");
                if (childValue != null) {
                    containerIds.add(compId);
                    try {
                        referencedChildIds.add(String.valueOf(childValue));
                    } catch (Exception e) {
                        errors.add("Component '" + compId + "' has invalid 'child' value");
                    }
                }

                String parentId = comp.optString("parent", "");
                if (!parentId.isEmpty()) {
                    referencedChildIds.add(compId);
                }

                // Check action: only functionCall allowed
                Object actionValue = getComponentProperty(comp, "action");
                if (actionValue != null) {
                    try {
                        JSONObject action = (JSONObject) actionValue;
                        boolean hasFunctionCall = action.has("functionCall");
                        boolean hasEvent = action.has("event");

                        if (hasEvent) {
                            errors.add("Component '" + compId + "' uses 'event' action which is not allowed");
                        }
                        if (!hasFunctionCall && !hasEvent) {
                            errors.add("Component '" + compId + "' has action but no 'functionCall' key");
                        }
                        if (hasFunctionCall) {
                            try {
                                JSONObject fc = action.getJSONObject("functionCall");
                                if (!fc.has("call")) {
                                    errors.add("Component '" + compId + "' functionCall missing 'call' field");
                                } else {
                                    String callName = fc.getString("call");
                                    if (!ALLOWED_FUNCTION_CALLS.contains(callName)) {
                                        errors.add("Component '" + compId + "' calls function '"
                                                + callName + "' which is not in allowlist "
                                                + ALLOWED_FUNCTION_CALLS);
                                    }
                                }
                            } catch (Exception e) {
                                errors.add("Component '" + compId + "' has invalid 'functionCall' object");
                            }
                        }
                    } catch (Exception e) {
                        errors.add("Component '" + compId + "' has invalid 'action' object");
                    }
                }
            } catch (Exception e) {
                errors.add("Component at index " + i + " is not a JSON object: " + e.getMessage());
            }
        }

        // Check children/child references exist
        for (String refId : referencedChildIds) {
            if (!allIds.contains(refId)) {
                errors.add("Referenced child id '" + refId + "' does not exist in components");
            }
        }

        // Check root component: exactly one root (not referenced as anyone's child)
        Set<String> rootIds = new HashSet<>(allIds);
        rootIds.removeAll(referencedChildIds);
        if (rootIds.isEmpty() && !allIds.isEmpty()) {
            errors.add("No root component found (every component is referenced as a child)");
        } else if (rootIds.size() > 1) {
            errors.add("Multiple root components found: " + rootIds + " (expected exactly one)");
        } else if (rootIds.size() == 1) {
            String rootId = rootIds.iterator().next();
            if (!"root".equals(rootId)) {
                errors.add("Root component id is '" + rootId + "'; Android SDK requires root id 'root'");
            }
        }
    }

    private static String getComponentType(JSONObject comp) {
        String type = comp.optString("component", "");
        if (type.isEmpty()) {
            type = comp.optString("type", "");
        }
        if (type.isEmpty()) {
            type = comp.optString("componentType", "");
        }
        return type;
    }

    private static Object getComponentProperty(JSONObject comp, String key) {
        if (comp.has(key)) {
            return comp.opt(key);
        }
        JSONObject properties = comp.optJSONObject("properties");
        if (properties != null && properties.has(key)) {
            return properties.opt(key);
        }
        return null;
    }

    public static JSONArray parseConcatenatedObjectsAsArray(String text) throws Exception {
        JSONArray arr = new JSONArray();
        int pos = 0;
        int len = text.length();
        while (pos < len) {
            // Skip whitespace and commas between objects
            while (pos < len && (text.charAt(pos) == ' ' || text.charAt(pos) == '\n'
                    || text.charAt(pos) == '\r' || text.charAt(pos) == '\t'
                    || text.charAt(pos) == ',')) {
                pos++;
            }
            if (pos >= len) break;
            if (text.charAt(pos) != '{') {
                throw new Exception("Expected '{' at position " + pos);
            }
            // Find matching closing brace, respecting nesting and strings
            int depth = 0;
            boolean inString = false;
            boolean escape = false;
            int start = pos;
            while (pos < len) {
                char ch = text.charAt(pos);
                if (escape) {
                    escape = false;
                } else if (ch == '\\' && inString) {
                    escape = true;
                } else if (ch == '"') {
                    inString = !inString;
                } else if (!inString) {
                    if (ch == '{') depth++;
                    else if (ch == '}') {
                        depth--;
                        if (depth == 0) {
                            String objText = text.substring(start, pos + 1);
                            arr.put(new JSONObject(objText));
                            pos++;
                            break;
                        }
                    }
                }
                pos++;
            }
            if (depth != 0) {
                throw new Exception("Unmatched '{' starting at position " + start);
            }
        }
        return arr;
    }
}
