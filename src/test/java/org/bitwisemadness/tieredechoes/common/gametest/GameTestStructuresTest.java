package org.bitwisemadness.tieredechoes.common.gametest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every GameTest needs a structure file named after it, and a missing one crashes the game test
 * server before any test runs — so the failure would read as "the harness is broken" rather than
 * "a test was renamed".
 *
 * <p>Caught here instead, where the message can say exactly what to regenerate.</p>
 *
 * <p>Ids come from {@link GameTestStructureGenerator} rather than from a pattern of this test's
 * own. The two used to carry separate copies of the same regex, which is the drift this file
 * exists to catch, reintroduced by the tool that fixes it.</p>
 */
class GameTestStructuresTest {

    private static final String REGENERATE = "Run: gradlew generateGameTestStructures";

    @Test
    void everyGameTestMethodHasAStructureTemplate() throws IOException {
        final List<String> missing = new ArrayList<>();

        for (final String id : GameTestStructureGenerator.discoverStructureIds(projectRoot())) {
            if (!Files.isRegularFile(structuresRoot().resolve(id + ".nbt"))) {
                missing.add(id + ".nbt");
            }
        }

        if (!missing.isEmpty()) {
            fail("Missing GameTest structure templates:\n  " + String.join("\n  ", missing)
                    + "\n" + REGENERATE);
        }
    }

    /** Guards the opposite drift: a template left behind by a test that no longer exists. */
    @Test
    void everyStructureTemplateBelongsToAnExistingGameTest() throws IOException {
        final List<String> ids =
                GameTestStructureGenerator.discoverStructureIds(projectRoot()).stream().toList();
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
                        + "\n" + REGENERATE);
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
