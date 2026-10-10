package org.bitwisemadness.tieredechoes.common.gametest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every GameTest needs a structure file named after it, and a missing one crashes the game test
 * server before any test runs — so the failure would read as "the harness is broken" rather than
 * "a test was renamed".
 *
 * <p>Caught here instead, where the message can say exactly what to regenerate.</p>
 */
class GameTestStructuresTest {

    private static final Pattern CLASS_DECLARATION =
            Pattern.compile("^\\s*public\\s+(?:final\\s+)?class\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern GAME_TEST_METHOD =
            Pattern.compile("@GameTest(?:\\s*\\([^)]*\\))?\\s+(?:public\\s+)?static\\s+void\\s+(\\w+)");

    @Test
    void everyGameTestMethodHasAStructureTemplate() throws IOException {
        final List<String> missing = new ArrayList<>();

        for (final GameTestRecord test : discoveredTests()) {
            if (!Files.isRegularFile(structuresRoot().resolve(test.id + ".nbt"))) {
                missing.add(test.id + ".nbt");
            }
        }

        if (!missing.isEmpty()) {
            fail("Missing GameTest structure templates:\n  " + String.join("\n  ", missing)
                    + "\nRun: pwsh tools/generate-gametest-structures.ps1");
        }
    }

    /** Guards the opposite drift: a template left behind by a test that no longer exists. */
    @Test
    void everyStructureTemplateBelongsToAnExistingGameTest() throws IOException {
        final List<String> ids = discoveredTests().stream().map(test -> test.id).toList();
        assertFalse(ids.isEmpty(), "no @GameTest methods were discovered; the scan itself has broken");

        final Path structures = structuresRoot();
        if (!Files.isDirectory(structures)) {
            return;
        }
        final List<String> stale = new ArrayList<>();
        try (var files = Files.list(structures)) {
            files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".nbt"))
                    .map(name -> name.substring(0, name.length() - ".nbt".length()))
                    .filter(name -> !ids.contains(name))
                    .forEach(stale::add);
        }

        assertFalse(!stale.isEmpty(),
                "Structure templates with no matching test: " + stale
                        + "\nRun: pwsh tools/generate-gametest-structures.ps1");
    }

    private record GameTestRecord(String id) {
    }

    private static List<GameTestRecord> discoveredTests() throws IOException {
        final List<GameTestRecord> tests = new ArrayList<>();
        for (final Path source : gameTestSources()) {
            final Matcher classMatcher = CLASS_DECLARATION.matcher(Files.readString(source));
            if (!classMatcher.find()) {
                continue;
            }
            final String className = classMatcher.group(1).toLowerCase();
            final Matcher methods = GAME_TEST_METHOD.matcher(Files.readString(source));
            while (methods.find()) {
                tests.add(new GameTestRecord(className + "." + methods.group(1).toLowerCase()));
            }
        }
        return tests;
    }

    private static List<Path> gameTestSources() throws IOException {
        final Path packageDir = projectRoot()
                .resolve("src").resolve("main").resolve("java")
                .resolve("org").resolve("bitwisemadness").resolve("tieredechoes")
                .resolve("common").resolve("gametest");
        if (!Files.isDirectory(packageDir)) {
            return List.of();
        }
        try (var files = Files.list(packageDir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java")).toList();
        }
    }

    private static Path structuresRoot() {
        return projectRoot().resolve("src").resolve("main").resolve("resources")
                .resolve("data").resolve("tieredechoes").resolve("structures");
    }

    /** Tolerates the suite being run from the repository's parent, as Gradle sometimes does. */
    private static Path projectRoot() {
        for (final Path candidate : List.of(Paths.get("."), Paths.get("tiered-echoes"))) {
            if (Files.isDirectory(candidate.resolve("src").resolve("main").resolve("java"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not locate the mod's sources; tests must run from the project directory");
    }
}
