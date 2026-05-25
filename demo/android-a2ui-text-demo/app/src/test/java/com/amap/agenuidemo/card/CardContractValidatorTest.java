package com.amap.agenuidemo.card;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CardContractValidatorTest {

    @Test
    public void validate_validTextSummary_returnsValid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_001")
                .put("cardType", "text_summary")
                .put("title", "今日天气")
                .put("content", "晴转多云，26°C")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void validate_missingCardType_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_002")
                .put("title", "Test")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("cardType")));
    }

    @Test
    public void validate_unknownCardType_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_003")
                .put("cardType", "unknown_type")
                .put("title", "Test")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Unknown cardType")));
    }

    @Test
    public void validate_textSummaryMissingContent_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_004")
                .put("cardType", "text_summary")
                .put("title", "今日天气")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("content")));
    }

    @Test
    public void validate_validTextList_returnsValid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_005")
                .put("cardType", "text_list")
                .put("title", "功能列表")
                .put("items", new JSONArray()
                        .put(new JSONObject().put("text", "天气"))
                        .put(new JSONObject().put("text", "导航")))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
    }

    @Test
    public void validate_textListEmptyItems_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_006")
                .put("cardType", "text_list")
                .put("title", "空列表")
                .put("items", new JSONArray())
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("items")));
    }

    @Test
    public void validate_textListMissingItems_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_007")
                .put("cardType", "text_list")
                .put("title", "无列表项")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
    }

    @Test
    public void validate_validImageTextList_returnsValid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_008")
                .put("cardType", "image_text_list")
                .put("title", "热门推荐")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("imageUrl", "https://example.com/img1.jpg")
                                .put("title", "智能助手")
                                .put("subtitle", "随时解答"))
                        .put(new JSONObject()
                                .put("imageUrl", "https://example.com/img2.jpg")
                                .put("title", "出行规划")
                                .put("subtitle", "最优路线")))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertTrue(result.isValid());
    }

    @Test
    public void validate_imageTextListMissingImageUrl_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_009")
                .put("cardType", "image_text_list")
                .put("title", "推荐")
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("title", "No image")
                                .put("subtitle", "desc")))
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("imageUrl")));
    }

    @Test
    public void validate_missingRequestId_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("cardType", "text_summary")
                .put("title", "Test")
                .put("content", "Hello")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("requestId")));
    }

    @Test
    public void validate_missingTitle_returnsInvalid() throws Exception {
        String json = new JSONObject()
                .put("requestId", "req_010")
                .put("cardType", "text_summary")
                .put("content", "Hello")
                .toString();

        CardContractValidator.ValidationResult result = CardContractValidator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("title")));
    }

    @Test
    public void validate_emptyJson_returnsInvalid() {
        CardContractValidator.ValidationResult result = CardContractValidator.validate("");
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("empty")));
    }

    @Test
    public void validate_invalidJson_returnsInvalid() {
        CardContractValidator.ValidationResult result = CardContractValidator.validate("not json");
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Invalid JSON")));
    }

    @Test
    public void validate_nullInput_returnsInvalid() {
        CardContractValidator.ValidationResult result = CardContractValidator.validate(null);
        assertFalse(result.isValid());
    }
}