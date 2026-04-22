package org.bitwisemadness.tieredechoes.common.data.validation;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ValidationReport {
    @Getter
    private final List<ValidationIssue> issues = new ArrayList<>();

    public void add(final ValidationIssue issue) {
        this.issues.add(issue);
    }

    public List<ValidationIssue> issues() {
        return Collections.unmodifiableList(this.issues);
    }

    public boolean hasErrors() {
        return this.errorCount() > 0;
    }

    public long errorCount() {
        return this.issues.stream().filter(issue -> issue.severity() == ValidationIssue.Severity.ERROR).count();
    }

    public long warningCount() {
        return this.issues.stream().filter(issue -> issue.severity() == ValidationIssue.Severity.WARNING).count();
    }
}
