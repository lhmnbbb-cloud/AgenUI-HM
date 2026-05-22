package com.amap.agenuidemo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts common LLM output shapes into the three A2UI protocol messages
 * expected by the Android SDK.
 */
public final class A2uiMessageNormalizer {

    private static final String VERSION = "v0.9";
    private static final String CATALOG_ID =
            "https://a2ui.org/specification/v0_9/standard_catalog.json";

    private static final Set<String> VALID_COMPONENT_TYPES = new HashSet<>(Arrays.asList(
            "Text", "Image", "Icon", "Video", "AudioPlayer",
            "Row", "Column", "List", "Card", "Tabs", "Modal", "Divider",
            "Button", "TextField", "CheckBox", "ChoicePicker", "Slider", "DateTimeInput",
            "RichText", "Table", "Web", "Carousel"
    ));

    private static final Map<String, String> COMPONENT_TYPE_BY_LOWER = new HashMap<>();

    static {
        for (String type : VALID_COMPONENT_TYPES) {
            COMPONENT_TYPE_BY_LOWER.put(type.toLowerCase(Locale.US), type);
        }
    }

    private A2uiMessageNormalizer() {
    }

    public static String[] normalizeMessages(String[] messages) throws Exception {
        if (messages == null || messages.length == 0) {
            throw new Exception("messages is empty");
        }

        JSONArray array = new JSONArray();
        for (String message : messages) {
            if (message == null || message.trim().isEmpty()) {
                array.put(new JSONObject());
                continue;
            }
            String text = stripMarkdownFences(message).trim();
            if (text.startsWith("[")) {
                return normalizeMessageArray(new JSONArray(text));
            }
            array.put(new JSONObject(text));
        }
        return normalizeMessageArray(array);
    }

    public static String[] normalizeRawText(String rawText) throws Exception {
        return normalizeMessageArray(extractMessageArray(rawText));
    }

    public static String stripMarkdownFences(String rawText) {
        if (rawText == null) {
            return "";
        }
        String text = rawText.trim();
        if (!text.startsWith("```")) {
            return text;
        }

        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (!line.trim().startsWith("```")) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static JSONArray extractMessageArray(String rawText) throws Exception {
        if (rawText == null || rawText.trim().isEmpty()) {
            throw new Exception("raw text is empty");
        }

        String text = stripMarkdownFences(rawText).trim();
        if (text.startsWith("{")) {
            try {
                return A2uiJsonValidator.parseConcatenatedObjectsAsArray(text);
            } catch (Exception ignored) {
                // Continue with wrapper parsing and substring extraction below.
            }
        }

        try {
            return parseMessageArray(text);
        } catch (Exception ignored) {
            // Continue with substring extraction below.
        }

        int arrayStart = text.indexOf('[');
        int arrayEnd = text.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            try {
                return parseMessageArray(text.substring(arrayStart, arrayEnd + 1));
            } catch (Exception ignored) {
                // Continue with object extraction below.
            }
        }

        int objectStart = text.indexOf('{');
        if (objectStart >= 0) {
            String objectText = text.substring(objectStart);
            try {
                return parseMessageArray(objectText);
            } catch (Exception ignored) {
                return A2uiJsonValidator.parseConcatenatedObjectsAsArray(objectText);
            }
        }

        throw new Exception("failed to find JSON payload in raw text");
    }

    private static JSONArray parseMessageArray(String text) throws Exception {
        String trimmed = stripMarkdownFences(text).trim();
        if (trimmed.startsWith("[")) {
            return new JSONArray(trimmed);
        }

        JSONObject obj = new JSONObject(trimmed);
        if (obj.has("messages")) {
            return obj.getJSONArray("messages");
        }
        if (obj.has("a2ui")) {
            return obj.getJSONArray("a2ui");
        }
        if (obj.has("components")) {
            return obj.getJSONArray("components");
        }

        JSONArray arr = new JSONArray();
        arr.put(obj);
        return arr;
    }

    private static String[] normalizeMessageArray(JSONArray input) throws Exception {
        if (input == null || input.length() == 0) {
            throw new Exception("message array is empty");
        }

        if (looksLikeComponentArray(input)) {
            String surfaceId = newSurfaceId();
            JSONObject create = buildCreateSurface(surfaceId);
            JSONObject update = buildUpdateComponents(surfaceId, input);
            normalizeUpdateComponents(update, surfaceId);
            return toMessages(create, update, new JSONObject());
        }

        JSONObject create = null;
        JSONObject update = null;
        JSONObject data = null;
        JSONArray looseComponents = new JSONArray();

        for (int i = 0; i < input.length(); i++) {
            Object item = input.get(i);
            if (item instanceof JSONArray) {
                return normalizeMessageArray((JSONArray) item);
            }
            if (item instanceof String) {
                String text = stripMarkdownFences((String) item).trim();
                if (text.startsWith("[") || text.startsWith("{")) {
                    return normalizeRawText(text);
                }
                continue;
            }
            if (!(item instanceof JSONObject)) {
                continue;
            }

            JSONObject obj = deepCopy((JSONObject) item);
            if (obj.has("messages")) {
                return normalizeMessageArray(obj.getJSONArray("messages"));
            }
            if (obj.has("a2ui")) {
                return normalizeMessageArray(obj.getJSONArray("a2ui"));
            }
            if (obj.has("createSurface")) {
                create = obj;
            } else if (obj.has("updateComponents")) {
                update = obj;
            } else if (obj.has("updateDataModel")) {
                data = obj;
            } else if (isComponentObject(obj)) {
                looseComponents.put(obj);
            } else if (i == 2) {
                data = obj;
            }
        }

        String surfaceId = chooseSurfaceId(create, update);
        if (surfaceId == null) {
            surfaceId = newSurfaceId();
        }

        if (create == null) {
            create = buildCreateSurface(surfaceId);
        } else {
            normalizeCreateSurface(create, surfaceId);
            surfaceId = create.getJSONObject("createSurface").getString("surfaceId");
        }

        if (update == null && looseComponents.length() > 0) {
            update = buildUpdateComponents(surfaceId, looseComponents);
        }
        if (update == null) {
            throw new Exception("missing updateComponents message");
        }

        normalizeUpdateComponents(update, surfaceId);
        if (data == null) {
            data = new JSONObject();
        } else if (data.has("updateDataModel")) {
            data.put("version", data.optString("version", VERSION));
        }

        return toMessages(create, update, data);
    }

    private static String[] toMessages(JSONObject create, JSONObject update, JSONObject data) {
        return new String[]{create.toString(), update.toString(), data.toString()};
    }

    private static boolean looksLikeComponentArray(JSONArray input) {
        boolean sawComponent = false;
        for (int i = 0; i < input.length(); i++) {
            Object item = input.opt(i);
            if (!(item instanceof JSONObject)) {
                continue;
            }
            JSONObject obj = (JSONObject) item;
            if (obj.has("createSurface") || obj.has("updateComponents") || obj.has("updateDataModel")
                    || obj.has("messages") || obj.has("a2ui")) {
                return false;
            }
            if (isComponentObject(obj)) {
                sawComponent = true;
            }
        }
        return sawComponent;
    }

    private static boolean isComponentObject(JSONObject obj) {
        return obj.has("id") || obj.has("component") || obj.has("type") || obj.has("componentType");
    }

    private static JSONObject buildCreateSurface(String surfaceId) throws Exception {
        JSONObject create = new JSONObject();
        create.put("version", VERSION);
        create.put("createSurface", new JSONObject()
                .put("surfaceId", surfaceId)
                .put("catalogId", CATALOG_ID));
        return create;
    }

    private static JSONObject buildUpdateComponents(String surfaceId, JSONArray components) throws Exception {
        JSONObject update = new JSONObject();
        update.put("version", VERSION);
        update.put("updateComponents", new JSONObject()
                .put("surfaceId", surfaceId)
                .put("components", components));
        return update;
    }

    private static void normalizeCreateSurface(JSONObject create, String fallbackSurfaceId) throws Exception {
        create.put("version", create.optString("version", VERSION));
        JSONObject body = create.optJSONObject("createSurface");
        if (body == null) {
            body = new JSONObject();
            create.put("createSurface", body);
        }
        if (!body.has("surfaceId") || body.optString("surfaceId").isEmpty()) {
            body.put("surfaceId", fallbackSurfaceId != null ? fallbackSurfaceId : newSurfaceId());
        }
        if (!body.has("catalogId") || body.optString("catalogId").isEmpty()) {
            body.put("catalogId", CATALOG_ID);
        }
    }

    private static void normalizeUpdateComponents(JSONObject update, String surfaceId) throws Exception {
        update.put("version", update.optString("version", VERSION));
        JSONObject body = update.optJSONObject("updateComponents");
        if (body == null) {
            body = new JSONObject();
            update.put("updateComponents", body);
        }
        if (!body.has("surfaceId") || body.optString("surfaceId").isEmpty()) {
            body.put("surfaceId", surfaceId);
        }

        JSONArray components;
        Object componentsObj = body.opt("components");
        if (componentsObj instanceof JSONArray) {
            components = (JSONArray) componentsObj;
        } else if (componentsObj instanceof JSONObject) {
            components = new JSONArray().put(componentsObj);
        } else {
            components = new JSONArray();
        }
        body.put("components", normalizeComponents(components));
    }

    private static JSONArray normalizeComponents(JSONArray components) throws Exception {
        List<JSONObject> result = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();

        for (int i = 0; i < components.length(); i++) {
            Object item = components.get(i);
            if (item instanceof JSONObject) {
                normalizeComponentRecursive(deepCopy((JSONObject) item),
                        i == 0 ? "root" : "component_" + i,
                        result,
                        usedIds);
            }
        }

        repairParentLinks(result);
        ensureSingleRoot(result);

        JSONArray normalized = new JSONArray();
        for (JSONObject component : result) {
            normalized.put(component);
        }
        return normalized;
    }

    private static void normalizeComponentRecursive(JSONObject component,
                                                    String fallbackId,
                                                    List<JSONObject> output,
                                                    Set<String> usedIds) throws Exception {
        String id = component.optString("id", "");
        if (id.isEmpty()) {
            id = uniqueId(fallbackId, usedIds);
            component.put("id", id);
        } else if (usedIds.contains(id)) {
            id = uniqueId(id, usedIds);
            component.put("id", id);
        } else {
            usedIds.add(id);
        }

        normalizeComponentType(component);
        JSONObject properties = normalizePropertiesContainer(component);
        normalizeTextAliases(component, properties);

        List<JSONObject> extraComponents = new ArrayList<>();
        normalizeChildren(component, properties, "children", id, extraComponents, output, usedIds);
        normalizeChildren(component, properties, "child", id, extraComponents, output, usedIds);
        normalizeButtonLabel(component, properties, extraComponents, output, usedIds);
        normalizeSingleChildContainers(component, properties, extraComponents);

        output.add(component);
        for (JSONObject extra : extraComponents) {
            output.add(extra);
        }
    }

    private static void normalizeComponentType(JSONObject component) throws Exception {
        String type = component.optString("component", "");
        if (type.isEmpty()) {
            type = component.optString("type", "");
        }
        if (type.isEmpty()) {
            type = component.optString("componentType", "");
        }
        if (type.isEmpty()) {
            type = "Text";
        }

        String canonical = COMPONENT_TYPE_BY_LOWER.get(type.toLowerCase(Locale.US));
        component.put("component", canonical != null ? canonical : type);
    }

    private static JSONObject normalizePropertiesContainer(JSONObject component) throws Exception {
        if (component.has("props") && !component.has("properties")) {
            component.put("properties", component.get("props"));
        }

        JSONObject properties = component.optJSONObject("properties");
        if (properties == null) {
            return component;
        }

        JSONArray names = component.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String key = names.getString(i);
                if (isMetadataKey(key) || properties.has(key)) {
                    continue;
                }
                properties.put(key, component.get(key));
            }
        }
        return properties;
    }

    private static boolean isMetadataKey(String key) {
        return "id".equals(key)
                || "type".equals(key)
                || "component".equals(key)
                || "componentType".equals(key)
                || "properties".equals(key)
                || "props".equals(key)
                || "parent".equals(key);
    }

    private static void normalizeTextAliases(JSONObject component, JSONObject properties) throws Exception {
        String componentType = component.optString("component", "");
        if (!"Text".equals(componentType) && !"RichText".equals(componentType)) {
            return;
        }
        if (properties.has("text")) {
            return;
        }
        if (properties.has("content")) {
            properties.put("text", properties.get("content"));
        } else if (properties.has("title")) {
            properties.put("text", properties.get("title"));
        } else if (properties.has("label")) {
            properties.put("text", properties.get("label"));
        }
    }

    private static void normalizeChildren(JSONObject component,
                                          JSONObject properties,
                                          String key,
                                          String parentId,
                                          List<JSONObject> extraComponents,
                                          List<JSONObject> output,
                                          Set<String> usedIds) throws Exception {
        if (!properties.has(key)) {
            return;
        }

        Object value = properties.get(key);
        if ("child".equals(key) && value instanceof JSONObject) {
            JSONObject child = (JSONObject) value;
            String childId = child.optString("id", "");
            if (childId.isEmpty()) {
                childId = parentId + "_child";
                child.put("id", childId);
            }
            properties.put("child", childId);
            normalizeComponentRecursive(child, childId, output, usedIds);
            return;
        }

        if (!(value instanceof JSONArray)) {
            return;
        }

        JSONArray source = (JSONArray) value;
        JSONArray childIds = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            Object childItem = source.get(i);
            if (childItem instanceof JSONObject) {
                JSONObject child = (JSONObject) childItem;
                String childId = child.optString("id", "");
                if (childId.isEmpty()) {
                    childId = parentId + "_child_" + i;
                    child.put("id", childId);
                }
                childIds.put(childId);
                normalizeComponentRecursive(child, childId, output, usedIds);
            } else {
                childIds.put(String.valueOf(childItem));
            }
        }
        properties.put("children", childIds);
    }

    private static void normalizeButtonLabel(JSONObject component,
                                             JSONObject properties,
                                             List<JSONObject> extraComponents,
                                             List<JSONObject> output,
                                             Set<String> usedIds) throws Exception {
        if (!"Button".equals(component.optString("component", "")) || properties.has("child")) {
            return;
        }

        Object label = null;
        if (properties.has("text")) {
            label = properties.get("text");
        } else if (properties.has("label")) {
            label = properties.get("label");
        }
        if (label == null) {
            return;
        }

        String textId = uniqueId(component.getString("id") + "_label", usedIds);
        JSONObject text = new JSONObject()
                .put("id", textId)
                .put("component", "Text")
                .put("text", label);
        properties.put("child", textId);
        extraComponents.add(text);
    }

    private static void normalizeSingleChildContainers(JSONObject component,
                                                       JSONObject properties,
                                                       List<JSONObject> extraComponents) throws Exception {
        String type = component.optString("component", "");
        if (!"Card".equals(type) && !"Button".equals(type)) {
            return;
        }
        if (properties.has("child") || !properties.has("children")) {
            return;
        }

        Object childrenObj = properties.get("children");
        if (!(childrenObj instanceof JSONArray)) {
            return;
        }

        JSONArray children = (JSONArray) childrenObj;
        if (children.length() == 0) {
            return;
        }
        if (children.length() == 1) {
            properties.put("child", children.getString(0));
            properties.remove("children");
            return;
        }

        String groupId = component.getString("id") + "_content";
        JSONObject group = new JSONObject()
                .put("id", groupId)
                .put("component", "Column")
                .put("children", children);
        properties.put("child", groupId);
        properties.remove("children");
        extraComponents.add(group);
    }

    private static void repairParentLinks(List<JSONObject> components) throws Exception {
        Map<String, JSONObject> byId = new HashMap<>();
        for (JSONObject component : components) {
            byId.put(component.getString("id"), component);
        }

        for (JSONObject component : components) {
            String parentId = component.optString("parent", "");
            if (parentId.isEmpty() || !byId.containsKey(parentId)) {
                continue;
            }

            JSONObject parent = byId.get(parentId);
            JSONObject parentProps = parent.optJSONObject("properties");
            if (parentProps == null) {
                parentProps = parent;
            }
            String childId = component.getString("id");

            if (parentProps.has("child")) {
                if (!childId.equals(parentProps.optString("child"))) {
                    JSONArray children = new JSONArray();
                    children.put(parentProps.getString("child"));
                    children.put(childId);
                    parentProps.remove("child");
                    parentProps.put("children", children);
                }
            } else {
                JSONArray children = parentProps.optJSONArray("children");
                if (children == null) {
                    children = new JSONArray();
                    parentProps.put("children", children);
                }
                if (!jsonArrayContains(children, childId)) {
                    children.put(childId);
                }
            }
        }
    }

    private static void ensureSingleRoot(List<JSONObject> components) throws Exception {
        if (components.isEmpty()) {
            return;
        }

        Set<String> allIds = new HashSet<>();
        Set<String> referencedIds = new HashSet<>();
        JSONObject explicitRoot = null;

        for (JSONObject component : components) {
            String id = component.getString("id");
            allIds.add(id);
            if ("root".equals(id)) {
                explicitRoot = component;
            }

            JSONObject properties = component.optJSONObject("properties");
            if (properties == null) {
                properties = component;
            }
            collectReferences(properties, referencedIds);
        }

        List<String> roots = new ArrayList<>();
        for (String id : allIds) {
            if (!referencedIds.contains(id)) {
                roots.add(id);
            }
        }

        if (roots.size() <= 1 && explicitRoot != null) {
            return;
        }
        if (roots.size() <= 1 && explicitRoot == null) {
            return;
        }

        if (explicitRoot == null) {
            explicitRoot = new JSONObject()
                    .put("id", "root")
                    .put("component", "Column")
                    .put("children", new JSONArray());
            components.add(0, explicitRoot);
        }

        JSONObject rootProps = explicitRoot.optJSONObject("properties");
        if (rootProps == null) {
            rootProps = explicitRoot;
        }
        JSONArray rootChildren = rootProps.optJSONArray("children");
        if (rootChildren == null) {
            rootChildren = new JSONArray();
            rootProps.put("children", rootChildren);
        }

        for (String rootId : roots) {
            if (!"root".equals(rootId) && !jsonArrayContains(rootChildren, rootId)) {
                rootChildren.put(rootId);
            }
        }
    }

    private static void collectReferences(JSONObject properties, Set<String> referencedIds) {
        JSONArray children = properties.optJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                referencedIds.add(String.valueOf(children.opt(i)));
            }
        }
        String child = properties.optString("child", "");
        if (!child.isEmpty()) {
            referencedIds.add(child);
        }
    }

    private static boolean jsonArrayContains(JSONArray array, String value) {
        for (int i = 0; i < array.length(); i++) {
            if (value.equals(String.valueOf(array.opt(i)))) {
                return true;
            }
        }
        return false;
    }

    private static String chooseSurfaceId(JSONObject create, JSONObject update) {
        try {
            if (create != null && create.has("createSurface")) {
                String id = create.getJSONObject("createSurface").optString("surfaceId", "");
                if (!id.isEmpty()) {
                    return id;
                }
            }
            if (update != null && update.has("updateComponents")) {
                String id = update.getJSONObject("updateComponents").optString("surfaceId", "");
                if (!id.isEmpty()) {
                    return id;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String uniqueId(String preferred, Set<String> usedIds) {
        String base = preferred == null || preferred.trim().isEmpty()
                ? "component"
                : preferred.replaceAll("[^A-Za-z0-9_-]", "_");
        String candidate = base;
        int index = 1;
        while (usedIds.contains(candidate)) {
            candidate = base + "_" + index;
            index++;
        }
        usedIds.add(candidate);
        return candidate;
    }

    private static String newSurfaceId() {
        return "llm_" + Long.toHexString(System.currentTimeMillis());
    }

    private static JSONObject deepCopy(JSONObject obj) throws Exception {
        return new JSONObject(obj.toString());
    }
}
