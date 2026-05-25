package com.amap.agenuidemo.card;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a card contract validation and template rendering pass.
 *
 * On success: valid=true, messages populated, errors empty.
 * On failure: valid=false, messages contain fallback A2UI, errors list reasons.
 */
public class CardRenderResult {

    private final boolean valid;
    private final String[] messages;
    private final List<String> errors;

    public CardRenderResult(boolean valid, String[] messages, List<String> errors) {
        this.valid = valid;
        this.messages = messages;
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public boolean isValid() {
        return valid;
    }

    public String[] getMessages() {
        return messages;
    }

    public List<String> getErrors() {
        return errors;
    }

    public String getFormattedErrors() {
        if (errors.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append("ERROR: ").append(errors.get(i));
        }
        return sb.toString();
    }
}