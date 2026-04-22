package org.bitwisemadness.tieredechoes.common.data.validation;

import java.util.Map;

public record ValidationIssue(Severity severity, String type, String message, Map<String, String> context) {
    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    public ValidationIssue {
        context = context == null ? Map.of() : Map.copyOf(context);
    }

    public ValidationIssue(final Severity severity, final String type, final String message) {
        this(severity, type, message, Map.of());
    }
}
