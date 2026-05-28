package com.amap.agenuidemo;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class CardHttpProviderTest {

    @Test
    public void buildRequestBodyObject_includesUserInputAndVersion() throws Exception {
        JSONObject result = CardHttpProvider.buildRequestBodyObject("北京天气");

        assertEquals("北京天气", result.getString("userInput"));
        assertEquals("0.1", result.getString("version"));
        assertTrue(result.has("supportedCardTypes"));
        assertEquals(2, result.getJSONArray("supportedCardTypes").length());
        assertEquals("sports_score_list", result.getJSONArray("supportedCardTypes").getString(0));
        assertEquals("weather_summary", result.getJSONArray("supportedCardTypes").getString(1));
    }

    @Test
    public void buildRequestBodyObject_handlesNullUserInput() throws Exception {
        JSONObject result = CardHttpProvider.buildRequestBodyObject(null);

        assertEquals("", result.getString("userInput"));
        assertEquals("0.1", result.getString("version"));
    }

    @Test
    public void buildRequestBodyJson_returnsValidJson() throws Exception {
        String json = CardHttpProvider.buildRequestBodyJson("上海天气");

        JSONObject obj = new JSONObject(json);
        assertEquals("上海天气", obj.getString("userInput"));
        assertEquals("0.1", obj.getString("version"));
    }

    @Test
    public void parseCardData_extractsNestedCardData() throws Exception {
        String response = new JSONObject()
                .put("cardData", new JSONObject()
                        .put("requestId", "test_123")
                        .put("cardType", "weather_summary")
                        .put("title", "今日天气"))
                .toString();

        String result = CardHttpProvider.parseCardData(response);
        JSONObject parsed = new JSONObject(result);

        assertEquals("test_123", parsed.getString("requestId"));
        assertEquals("weather_summary", parsed.getString("cardType"));
        assertEquals("今日天气", parsed.getString("title"));
    }

    @Test
    public void parseCardData_handlesCardDataAsString() throws Exception {
        String cardDataJson = new JSONObject()
                .put("requestId", "test_456")
                .put("cardType", "text_summary")
                .put("title", "Title")
                .put("content", "Content")
                .toString();

        String response = new JSONObject()
                .put("cardData", cardDataJson)
                .toString();

        String result = CardHttpProvider.parseCardData(response);
        JSONObject parsed = new JSONObject(result);

        assertEquals("test_456", parsed.getString("requestId"));
        assertEquals("text_summary", parsed.getString("cardType"));
    }

    @Test
    public void parseCardData_returnsDirectObjectWhenNoCardDataKey() throws Exception {
        String response = new JSONObject()
                .put("requestId", "test_789")
                .put("cardType", "sports_score_list")
                .put("title", "NBA")
                .toString();

        String result = CardHttpProvider.parseCardData(response);
        JSONObject parsed = new JSONObject(result);

        assertEquals("test_789", parsed.getString("requestId"));
        assertEquals("sports_score_list", parsed.getString("cardType"));
    }

    @Test(expected = CardHttpProvider.CardHttpException.class)
    public void parseCardData_throwsExceptionWhenResponseIsNull() throws Exception {
        CardHttpProvider.parseCardData(null);
    }

    @Test(expected = CardHttpProvider.CardHttpException.class)
    public void parseCardData_throwsExceptionWhenResponseIsEmpty() throws Exception {
        CardHttpProvider.parseCardData("");
    }

    @Test(expected = CardHttpProvider.CardHttpException.class)
    public void parseCardData_throwsExceptionWhenResponseIsWhitespace() throws Exception {
        CardHttpProvider.parseCardData("   ");
    }

    @Test
    public void setEndpointUrl_updatesUrl() throws Exception {
        CardHttpProvider provider = new CardHttpProvider();
        assertEquals("http://10.0.2.2:8766/generate-card", provider.getEndpointUrl());

        provider.setEndpointUrl("http://example.com/card");
        assertEquals("http://example.com/card", provider.getEndpointUrl());
    }

    @Test
    public void setEndpointUrl_ignoresNull() throws Exception {
        CardHttpProvider provider = new CardHttpProvider();
        String original = provider.getEndpointUrl();

        provider.setEndpointUrl(null);
        assertEquals(original, provider.getEndpointUrl());
    }

    @Test
    public void setEndpointUrl_ignoresEmptyString() throws Exception {
        CardHttpProvider provider = new CardHttpProvider();
        String original = provider.getEndpointUrl();

        provider.setEndpointUrl("");
        assertEquals(original, provider.getEndpointUrl());

        provider.setEndpointUrl("   ");
        assertEquals(original, provider.getEndpointUrl());
    }

    @Test
    public void cardHttpException_savesRawOutput() throws Exception {
        CardHttpProvider.CardHttpException e =
                new CardHttpProvider.CardHttpException("error message", "{\"raw\":\"data\"}");

        assertEquals("error message", e.getMessage());
        assertEquals("{\"raw\":\"data\"}", e.getRawOutput());
    }

    @Test
    public void cardHttpException_handlesNullRawOutput() throws Exception {
        CardHttpProvider.CardHttpException e =
                new CardHttpProvider.CardHttpException("error", null);

        assertEquals("", e.getRawOutput());
    }
}
