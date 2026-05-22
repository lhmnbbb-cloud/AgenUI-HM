package com.amap.agenuidemo;

/**
 * UI generation provider interface.
 * Implementations convert user input text into A2UI protocol JSON.
 */
public interface UiGenerationProvider {

    /**
     * Generate A2UI protocol JSON messages for the given user input.
     *
     * @param userInput user input text (e.g. "天气", "设置")
     * @return A2UI protocol messages array:
     *         [0] = createSurface JSON
     *         [1] = updateComponents JSON
     *         [2] = updateDataModel JSON (may be empty "{}")
     */
    String[] generate(String userInput);
}
