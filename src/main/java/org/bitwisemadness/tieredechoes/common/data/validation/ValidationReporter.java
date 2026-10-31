package org.bitwisemadness.tieredechoes.common.data.validation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import lombok.experimental.UtilityClass;
import net.minecraftforge.fml.loading.FMLPaths;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.config.TieredEchoesConfig;

@UtilityClass
public class ValidationReporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicReference<ValidationReport> LAST_REPORT = new AtomicReference<>();

    public static void log(final ValidationReport report) {
        LAST_REPORT.set(report);
        for (final ValidationIssue issue : report.issues()) {
            switch (issue.severity()) {
                case INFO -> TieredEchoes.LOGGER.info("[Validation][{}] {} {}", issue.type(), issue.message(), issue.context());
                case WARNING -> TieredEchoes.LOGGER.warn("[Validation][{}] {} {}", issue.type(), issue.message(), issue.context());
                case ERROR -> TieredEchoes.LOGGER.error("[Validation][{}] {} {}", issue.type(), issue.message(), issue.context());
            }
        }
        TieredEchoes.LOGGER.info("[Validation] {} issue(s), {} error(s), {} warning(s)", report.issues().size(), report.errorCount(), report.warningCount());
        if (TieredEchoesConfig.WRITE_VALIDATION_JSON.get()) {
            writeJsonReport(report);
        }
    }


    public static ValidationReport getLastReport() {
        return LAST_REPORT.get();
    }

    private static void writeJsonReport(final ValidationReport report) {
        final JsonObject root = new JsonObject();
        root.addProperty("has_errors", report.hasErrors());
        root.addProperty("error_count", report.errorCount());
        root.addProperty("warning_count", report.warningCount());
        final JsonArray issues = new JsonArray();
        for (final ValidationIssue issue : report.issues()) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("severity", issue.severity().name());
            entry.addProperty("type", issue.type());
            entry.addProperty("message", issue.message());
            final JsonObject ctx = new JsonObject();
            for (final var c : issue.context().entrySet()) {
                ctx.addProperty(c.getKey(), c.getValue());
            }
            entry.add("context", ctx);
            issues.add(entry);
        }
        root.add("issues", issues);
        final Path target = FMLPaths.GAMEDIR.get().resolve("tieredechoes-validation-report.json");
        try {
            Files.writeString(target, GSON.toJson(root));
        } catch (final IOException exception) {
            TieredEchoes.LOGGER.warn("Failed to write validation report to {}", target, exception);
        }
    }
}
