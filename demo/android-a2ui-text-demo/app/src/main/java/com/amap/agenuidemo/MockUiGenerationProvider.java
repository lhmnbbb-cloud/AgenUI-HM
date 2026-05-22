package com.amap.agenuidemo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.UUID;

/**
 * Mock UI generation provider that generates A2UI v0.9 protocol JSON
 * based on keyword matching from user input.
 *
 * Keyword mapping:
 * - "天气" / "weather" -> weather card
 * - "设置" / "settings" -> settings list
 * - "表单" / "form" -> form components
 * - "列表" / "list" -> list components
 * - Other input -> generic info card
 */
public class MockUiGenerationProvider implements UiGenerationProvider {

    private static final String A2UI_VERSION = "v0.9";
    private static final String CATALOG_ID = "https://a2ui.org/specification/v0_9/standard_catalog.json";

    @Override
    public String[] generate(String userInput) {
        String surfaceId = "demo_" + UUID.randomUUID().toString().substring(0, 8);
        String inputLower = userInput.toLowerCase(Locale.getDefault());

        String updateComponentsJson;
        String updateDataModelJson = "{}";

        if (inputLower.contains("天气") || inputLower.contains("weather")) {
            updateComponentsJson = buildWeatherCard(surfaceId);
        } else if (inputLower.contains("设置") || inputLower.contains("settings")) {
            updateComponentsJson = buildSettingsList(surfaceId);
        } else if (inputLower.contains("表单") || inputLower.contains("form")) {
            updateComponentsJson = buildForm(surfaceId);
        } else if (inputLower.contains("列表") || inputLower.contains("list")) {
            updateComponentsJson = buildList(surfaceId);
        } else {
            updateComponentsJson = buildInfoCard(surfaceId, userInput);
        }

        String createSurfaceJson = buildCreateSurface(surfaceId);

        return new String[]{createSurfaceJson, updateComponentsJson, updateDataModelJson};
    }

    private String buildCreateSurface(String surfaceId) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", A2UI_VERSION);

            JSONObject createSurface = new JSONObject();
            createSurface.put("surfaceId", surfaceId);
            createSurface.put("catalogId", CATALOG_ID);

            root.put("createSurface", createSurface);
            return root.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildWeatherCard(String surfaceId) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", A2UI_VERSION);

            JSONObject update = new JSONObject();
            update.put("surfaceId", surfaceId);

            JSONArray components = new JSONArray();

            // Root Column
            JSONObject rootCol = new JSONObject();
            rootCol.put("id", "root");
            rootCol.put("component", "Column");
            rootCol.put("children", new JSONArray().put("weather-card"));
            rootCol.put("align", "stretch");
            rootCol.put("styles", new JSONObject()
                    .put("padding", "20px")
                    .put("gap", "16px")
                    .put("background-color", "#F5F5F5"));
            components.put(rootCol);

            // Card
            JSONObject card = new JSONObject();
            card.put("id", "weather-card");
            card.put("component", "Card");
            card.put("child", "card-content");
            card.put("styles", new JSONObject()
                    .put("width", "auto")
                    .put("height", "auto")
                    .put("padding", "24px")
                    .put("border-radius", "16px")
                    .put("background-color", "#FFFFFF")
                    .put("filter", "drop-shadow(0px 6px 24px 0px rgba(0, 0, 0, 0.08))"));
            components.put(card);

            // Card content Column
            JSONObject content = new JSONObject();
            content.put("id", "card-content");
            content.put("component", "Column");
            content.put("children", new JSONArray()
                    .put("weather-title")
                    .put("weather-temp")
                    .put("weather-desc"));
            content.put("align", "stretch");
            components.put(content);

            // Title
            JSONObject title = new JSONObject();
            title.put("id", "weather-title");
            title.put("component", "Text");
            title.put("text", "今日天气");
            title.put("variant", "h2");
            title.put("styles", new JSONObject()
                    .put("text-align", "left"));
            components.put(title);

            // Temperature
            JSONObject temp = new JSONObject();
            temp.put("id", "weather-temp");
            temp.put("component", "Text");
            temp.put("text", "26°C / 18°C");
            temp.put("variant", "h3");
            temp.put("styles", new JSONObject()
                    .put("text-align", "left")
                    .put("color", "#6200EE"));
            components.put(temp);

            // Description
            JSONObject desc = new JSONObject();
            desc.put("id", "weather-desc");
            desc.put("component", "Text");
            desc.put("text", "晴转多云，适合户外活动");
            desc.put("variant", "body");
            desc.put("styles", new JSONObject()
                    .put("text-align", "left")
                    .put("color", "#000000E6"));
            components.put(desc);

            update.put("components", components);
            root.put("updateComponents", update);
            return root.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildSettingsList(String surfaceId) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", A2UI_VERSION);

            JSONObject update = new JSONObject();
            update.put("surfaceId", surfaceId);

            JSONArray components = new JSONArray();

            // Root Column
            JSONObject rootCol = new JSONObject();
            rootCol.put("id", "root");
            rootCol.put("component", "Column");
            rootCol.put("children", new JSONArray()
                    .put("settings-title")
                    .put("settings-list"));
            rootCol.put("align", "stretch");
            rootCol.put("styles", new JSONObject()
                    .put("padding", "20px")
                    .put("gap", "16px")
                    .put("background-color", "#F5F5F5"));
            components.put(rootCol);

            // Title
            JSONObject title = new JSONObject();
            title.put("id", "settings-title");
            title.put("component", "Text");
            title.put("text", "常用设置");
            title.put("variant", "h2");
            components.put(title);

            // List
            JSONObject list = new JSONObject();
            list.put("id", "settings-list");
            list.put("component", "List");
            list.put("children", new JSONArray()
                    .put("s1")
                    .put("s2")
                    .put("s3")
                    .put("s4"));
            list.put("align", "stretch");
            components.put(list);

            // Setting items
            String[] items = {"WiFi 网络", "蓝牙连接", "通知管理", "隐私与安全"};
            for (int i = 0; i < items.length; i++) {
                JSONObject item = new JSONObject();
                item.put("id", "s" + (i + 1));
                item.put("component", "Text");
                item.put("text", items[i]);
                item.put("variant", "body");
                item.put("styles", new JSONObject().put("padding", "8px 16px"));
                components.put(item);
            }

            update.put("components", components);
            root.put("updateComponents", update);
            return root.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildForm(String surfaceId) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", A2UI_VERSION);

            JSONObject update = new JSONObject();
            update.put("surfaceId", surfaceId);

            JSONArray components = new JSONArray();

            // Root Column
            JSONObject rootCol = new JSONObject();
            rootCol.put("id", "root");
            rootCol.put("component", "Column");
            rootCol.put("children", new JSONArray()
                    .put("form-card"));
            rootCol.put("align", "stretch");
            rootCol.put("styles", new JSONObject()
                    .put("padding", "20px")
                    .put("background-color", "#F5F5F5"));
            components.put(rootCol);

            // Card
            JSONObject card = new JSONObject();
            card.put("id", "form-card");
            card.put("component", "Card");
            card.put("child", "form-content");
            card.put("styles", new JSONObject()
                    .put("width", "auto")
                    .put("padding", "24px")
                    .put("border-radius", "16px")
                    .put("background-color", "#FFFFFF")
                    .put("filter", "drop-shadow(0px 6px 24px 0px rgba(0, 0, 0, 0.08))"));
            components.put(card);

            // Form content Column
            JSONObject content = new JSONObject();
            content.put("id", "form-content");
            content.put("component", "Column");
            content.put("children", new JSONArray()
                    .put("form-title")
                    .put("name-field")
                    .put("phone-field")
                    .put("agree-check")
                    .put("submit-btn"));
            content.put("align", "stretch");
            content.put("styles", new JSONObject().put("gap", "16px"));
            components.put(content);

            // Form title
            JSONObject formTitle = new JSONObject();
            formTitle.put("id", "form-title");
            formTitle.put("component", "Text");
            formTitle.put("text", "用户注册");
            formTitle.put("variant", "h2");
            components.put(formTitle);

            // Name TextField
            JSONObject nameField = new JSONObject();
            nameField.put("id", "name-field");
            nameField.put("component", "TextField");
            nameField.put("label", "姓名");
            nameField.put("placeholder", "请输入您的姓名");
            nameField.put("value", "");
            components.put(nameField);

            // Phone TextField
            JSONObject phoneField = new JSONObject();
            phoneField.put("id", "phone-field");
            phoneField.put("component", "TextField");
            phoneField.put("label", "手机号");
            phoneField.put("placeholder", "请输入手机号");
            phoneField.put("value", "");
            components.put(phoneField);

            // CheckBox
            JSONObject agreeCheck = new JSONObject();
            agreeCheck.put("id", "agree-check");
            agreeCheck.put("component", "CheckBox");
            agreeCheck.put("label", "同意服务条款");
            agreeCheck.put("value", false);
            components.put(agreeCheck);

            // Submit Button
            JSONObject submitBtn = new JSONObject();
            submitBtn.put("id", "submit-btn");
            submitBtn.put("component", "Button");
            submitBtn.put("text", "提交");
            submitBtn.put("variant", "primary");
            components.put(submitBtn);

            update.put("components", components);
            root.put("updateComponents", update);
            return root.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildList(String surfaceId) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", A2UI_VERSION);

            JSONObject update = new JSONObject();
            update.put("surfaceId", surfaceId);

            JSONArray components = new JSONArray();

            // Root Column
            JSONObject rootCol = new JSONObject();
            rootCol.put("id", "root");
            rootCol.put("component", "Column");
            rootCol.put("children", new JSONArray()
                    .put("list-title")
                    .put("demo-list"));
            rootCol.put("align", "stretch");
            rootCol.put("styles", new JSONObject()
                    .put("padding", "20px")
                    .put("gap", "16px")
                    .put("background-color", "#F5F5F5"));
            components.put(rootCol);

            // Title
            JSONObject title = new JSONObject();
            title.put("id", "list-title");
            title.put("component", "Text");
            title.put("text", "功能列表");
            title.put("variant", "h2");
            components.put(title);

            // List
            JSONObject list = new JSONObject();
            list.put("id", "demo-list");
            list.put("component", "List");
            list.put("children", new JSONArray()
                    .put("li1").put("li2").put("li3").put("li4").put("li5"));
            list.put("align", "stretch");
            components.put(list);

            String[] items = {"实时天气查询", "智能导航规划", "语音交互控制", "个性化推荐", "系统状态监控"};
            for (int i = 0; i < items.length; i++) {
                JSONObject item = new JSONObject();
                item.put("id", "li" + (i + 1));
                item.put("component", "Text");
                item.put("text", items[i]);
                item.put("variant", "body");
                item.put("styles", new JSONObject().put("padding", "12px 16px"));
                components.put(item);
            }

            update.put("components", components);
            root.put("updateComponents", update);
            return root.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildInfoCard(String surfaceId, String userInput) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", A2UI_VERSION);

            JSONObject update = new JSONObject();
            update.put("surfaceId", surfaceId);

            JSONArray components = new JSONArray();

            // Root Column
            JSONObject rootCol = new JSONObject();
            rootCol.put("id", "root");
            rootCol.put("component", "Column");
            rootCol.put("children", new JSONArray().put("info-card"));
            rootCol.put("align", "stretch");
            rootCol.put("styles", new JSONObject()
                    .put("padding", "20px")
                    .put("gap", "16px")
                    .put("background-color", "#F5F5F5"));
            components.put(rootCol);

            // Card
            JSONObject card = new JSONObject();
            card.put("id", "info-card");
            card.put("component", "Card");
            card.put("child", "info-content");
            card.put("styles", new JSONObject()
                    .put("width", "auto")
                    .put("height", "auto")
                    .put("padding", "24px")
                    .put("border-radius", "16px")
                    .put("background-color", "#FFFFFF")
                    .put("filter", "drop-shadow(0px 6px 24px 0px rgba(0, 0, 0, 0.08))"));
            components.put(card);

            // Card content
            JSONObject content = new JSONObject();
            content.put("id", "info-content");
            content.put("component", "Column");
            content.put("children", new JSONArray()
                    .put("info-title")
                    .put("info-body"));
            content.put("align", "stretch");
            components.put(content);

            // Title - show the user input
            JSONObject title = new JSONObject();
            title.put("id", "info-title");
            title.put("component", "Text");
            title.put("text", userInput);
            title.put("variant", "h2");
            title.put("styles", new JSONObject()
                    .put("text-align", "left")
                    .put("font-weight", "bold"));
            components.put(title);

            // Body
            JSONObject body = new JSONObject();
            body.put("id", "info-body");
            body.put("component", "Text");
            body.put("text", "这是一条由 A2UI 协议生成的信息卡片。输入\"天气\"、\"设置\"、\"表单\"、\"列表\"可查看不同 UI 效果。");
            body.put("variant", "body");
            body.put("styles", new JSONObject()
                    .put("text-align", "left")
                    .put("color", "#000000E6"));
            components.put(body);

            update.put("components", components);
            root.put("updateComponents", update);
            return root.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
}