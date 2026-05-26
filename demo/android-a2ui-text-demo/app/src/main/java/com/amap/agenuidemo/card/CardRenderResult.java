package com.amap.agenuidemo.card;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a card contract validation and template rendering pass.
 *
 * On success: valid=true, messages populated, errors empty, warnings may contain risk hints.
 * On failure: valid=false, messages contain fallback A2UI, errors list reasons.
 */
public class CardRenderResult {

    private final boolean valid;
    private final String[] messages;
    private final List<String> errors;
    private final List<String> warnings;

    public CardRenderResult(boolean valid, String[] messages, List<String> errors, List<String> warnings) {
        this.valid = valid;
        this.messages = messages;
        this.errors = errors != null ? errors : new ArrayList<>();
        this.warnings = warnings != null ? warnings : new ArrayList<>();
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

    public List<String> getWarnings() {
        return warnings;
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

    public String getFormattedWarnings() {
        if (warnings.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < warnings.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append("WARNING: ").append(warnings.get(i));
        }
        return sb.toString();
    }

    /**
     * Combined report of errors and warnings, suitable for debug display.
     */
    public String getFormattedReport() {
        StringBuilder sb = new StringBuilder();
        String errs = getFormattedErrors();
        String warns = getFormattedWarnings();
        if (!errs.isEmpty()) sb.append(errs);
        if (!errs.isEmpty() && !warns.isEmpty()) sb.append("\n");
        if (!warns.isEmpty()) sb.append(warns);
        return sb.toString();
    }
}