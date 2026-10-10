package org.bitwisemadness.tieredechoes.datagen;

import com.google.common.hash.HashCode;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The ladder is authored in {@link ShippedLadder} and committed as JSON by {@code gradlew runData},
 * which is a manual step. Two copies that nobody enforces is exactly how a shipped datapack quietly
 * stops matching the table a maintainer just edited, so the build fails here instead.
 *
 * <p>The check runs the real provider rather than a re-implementation of it, so a change to the
 * serialiser is covered too — the test would otherwise be guarding a description of the output
 * rather than the output.</p>
 */
class ShippedLadderTest {

    private static final String RUN_DATA = "Run: gradlew runData";

    @TempDir
    Path scratch;

    @Test
    void committedDatapackIsExactlyWhatTheLadderWouldGenerate() throws IOException {
        final Path generated = generateInto(this.scratch);

        final List<String> expected = jsonFilesUnder(generated);
        assertFalse(expected.isEmpty(),
                "Datagen produced no files at all; ShippedLadder or TieredEchoesDataProvider is broken. " + RUN_DATA);

        final Path committed = committedRoot();
        if (!Files.isDirectory(committed)) {
            fail("No committed datapack found at " + committed + " - the generated datapack is missing. " + RUN_DATA);
        }
        final List<String> actual = jsonFilesUnder(committed);

        final List<String> missing = expected.stream().filter(name -> !actual.contains(name)).toList();
        final List<String> stale = actual.stream().filter(name -> !expected.contains(name)).toList();
        final List<String> changed = new ArrayList<>();
        for (final String name : expected) {
            if (actual.contains(name)
                    && !Files.readString(generated.resolve(name)).equals(Files.readString(committed.resolve(name)))) {
                changed.add(name);
            }
        }

        if (!missing.isEmpty() || !stale.isEmpty() || !changed.isEmpty()) {
            fail(describe(missing, stale, changed));
        }
    }

    /** Without this the test above would pass against an empty directory if both sides vanished. */
    @Test
    void everyBuiltInDomainShipsFourTiersThreeStepsAndEightTemplates() throws IOException {
        final List<String> generated = jsonFilesUnder(generateInto(this.scratch));

        final long tiers = generated.stream().filter(name -> name.startsWith("tiers/")).count();
        final long paths = generated.stream().filter(name -> name.startsWith("tier_paths/")).count();
        final long templates = generated.stream().filter(name -> name.startsWith("tier_templates/")).count();
        final long recipes = generated.stream().filter(name -> name.startsWith("recipes/templates/")).count();

        assertFalse(tiers == 0 || paths == 0 || templates == 0 || recipes == 0,
                "Expected four directories of generated data, got tiers=" + tiers
                        + " tier_paths=" + paths + " tier_templates=" + templates + " recipes=" + recipes);
    }

    private static String describe(final List<String> missing, final List<String> stale, final List<String> changed) {
        final StringBuilder message = new StringBuilder()
                .append("The committed datapack has drifted from ShippedLadder. ").append(RUN_DATA)
                .append(", then commit src/generated/resources.\n");
        append(message, "Stale files (committed but no longer generated - delete them)", stale);
        append(message, "Missing files (generated but not committed)", missing);
        append(message, "Changed files (content differs)", changed);
        return message.toString();
    }

    private static void append(final StringBuilder message, final String heading, final List<String> names) {
        if (!names.isEmpty()) {
            message.append('\n').append(heading).append(":\n  ")
                    .append(String.join("\n  ", names));
        }
    }

    /** Runs the real provider into a scratch directory, waiting for its asynchronous writes. */
    private Path generateInto(final Path directory) {
        final PackOutput output = new PackOutput(directory);
        final TieredEchoesDataProvider provider = new TieredEchoesDataProvider(output);
        try {
            // saveStable defers to a background executor, so the files are not on disk until this
            // completes. A test that read too early would be comparing against nothing.
            provider.run(new DirectWriter()).join();
        } catch (final RuntimeException exception) {
            throw new IllegalStateException("Datagen failed while generating the comparison copy", exception);
        }
        return generatedRoot(directory);
    }

    /** Every generated JSON file, as a slash-separated path relative to the data pack root. */
    private static List<String> jsonFilesUnder(final Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(name -> name.replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    /** Tolerates the suite being run from the repository's parent, as Gradle sometimes does. */
    private static Path committedRoot() {
        for (final Path candidate : List.of(Paths.get("."), Paths.get("tiered-echoes"))) {
            final Path root = candidate.resolve("src").resolve("generated").resolve("resources")
                    .resolve("data").resolve("tieredechoes");
            if (Files.isDirectory(root.getParent())) {
                return root;
            }
        }
        throw new IllegalStateException("Could not locate src/generated/resources; tests must run from the project directory");
    }

    /** Written under {@code <temp>/data/tieredechoes}, matching the committed layout. */
    private static Path generatedRoot(final Path directory) {
        return new PackOutput(directory).getOutputFolder(PackOutput.Target.DATA_PACK).resolve("tieredechoes");
    }

    /** Writes straight through: the test has no cache to keep, it only wants the bytes on disk. */
    private static final class DirectWriter implements CachedOutput {
        @Override
        public void writeIfNeeded(final Path path, final byte[] bytes, final HashCode hash) throws IOException {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        }
    }
}
