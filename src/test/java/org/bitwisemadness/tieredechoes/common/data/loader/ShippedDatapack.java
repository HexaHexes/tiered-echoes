package org.bitwisemadness.tieredechoes.common.data.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationIssue;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationReport;
import org.bitwisemadness.tieredechoes.common.path.PathService;
import org.bitwisemadness.tieredechoes.common.path.TierEdge;
import org.bitwisemadness.tieredechoes.common.policy.PolicyService;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.bitwisemadness.tieredechoes.common.template.TemplateDefinition;
import org.bitwisemadness.tieredechoes.common.template.TemplateService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads the datapack this repository ships and pushes it through the same parse, domain-resolution
 * and de-duplication steps the live reloader uses.
 *
 * <p>The shipped ladder lives in {@code src/generated/resources}, written by {@code gradlew runData}
 * from {@code ShippedLadder} and committed alongside everything else. Nothing in Java re-states it:
 * a missing tier file removes the tier, exactly as it would for a third-party pack.</p>
 *
 * <p>The only part skipped is the resource-manager walk: files are read straight off disk instead,
 * so the test exercises the parsing contract rather than Minecraft's pack plumbing. Being in the
 * reloader's package is what makes the package-private parse helpers reachable.</p>
 *
 * <p>This doubles as the suite's reset hook: tests that mutate the global services put the shipped
 * state back in {@code @AfterEach} so one test's graph cannot leak into another's.</p>
 */
public final class ShippedDatapack {

    private static final String NAMESPACE = "tieredechoes";

    private ShippedDatapack() {
    }

    /**
     * Resolves {@code data/tieredechoes} inside the generated datapack, tolerating the project
     * being run from a parent folder.
     */
    public static Path dataRoot() {
        for (final Path candidate : generatedRoots()) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Shipped datapack not found; tests must run with the project directory as the working dir. Run 'gradlew runData' to regenerate it.");
    }

    /** Candidate locations of the generated datapack, whichever layout the build is running under. */
    private static List<Path> generatedRoots() {
        return List.of(
                Paths.get("src", "generated", "resources", "data", NAMESPACE),
                Paths.get("tiered-echoes", "src", "generated", "resources", "data", NAMESPACE));
    }

    /**
     * @param tiers    the shipped {@code tiers} declarations, de-duplicated; there is no Java
     *                 fallback, so a file that is absent is a tier that does not exist
     * @param edges    the same for the transition graph
     * @param templates the shipped {@code tier_templates} declarations
     * @param report   parse and de-duplication findings; {@link PathService#validate} findings are
     *                 not included because they need the final applied state
     */
    public record Loaded(List<TierDefinition> tiers, List<TierEdge> edges, List<TemplateDefinition> templates, ValidationReport report) {
    }

    /** Parses without touching the live services. */
    public static Loaded load() {
        return loadFrom(dataRoot());
    }

    /**
     * Parses an arbitrary pack root. Everything the reloader decides is decided here too, so this is
     * how a test can ask what a pack that omits or overrides a file would actually produce.
     */
    public static Loaded loadFrom(final Path root) {
        final ValidationReport report = new ValidationReport();

        final List<TierDefinition> tiers = new ArrayList<>();
        for (final JsonFile file : jsonFiles(root, TieredEchoesDataReloader.TIER_DIRECTORY, report, "tier_parse_failure")) {
            if (!TieredEchoesDataReloader.isCompatEligible(file.json())) {
                continue;
            }
            try {
                tiers.add(TieredEchoesDataReloader.parseTier(file.id(), file.json()));
            } catch (final RuntimeException exception) {
                report.add(failure(file, exception));
            }
        }
        final List<TierDefinition> resolvedTiers = TieredEchoesDataReloader.deduplicateTiers(tiers, report);

        final List<TierEdge> edges = new ArrayList<>();
        int registrationOrder = 10_000;
        for (final JsonFile file : jsonFiles(root, TieredEchoesDataReloader.TIER_PATH_DIRECTORY, report, "path_parse_failure")) {
            if (!TieredEchoesDataReloader.isCompatEligible(file.json())) {
                continue;
            }
            final Optional<TierDomain> fileDomain = TieredEchoesDataReloader.optionalDomain(file.json(), "domain");
            final JsonArray declared = GsonHelper.getAsJsonArray(file.json(), "edges", new JsonArray());
            for (int index = 0; index < declared.size(); index++) {
                final JsonObject edge = declared.get(index).getAsJsonObject();
                try {
                    final TierDomain domain = TieredEchoesDataReloader.resolveEdgeDomain(edge, fileDomain,
                            resolvedTiers, report, file.id(), registrationOrder);
                    if (domain == null) {
                        continue;
                    }
                    edges.add(TieredEchoesDataReloader.parseEdge(edge, domain, registrationOrder++, file.id().toString()));
                } catch (final RuntimeException exception) {
                    report.add(failure(file, exception));
                }
            }
        }

        final List<TemplateDefinition> templates = new ArrayList<>();
        for (final JsonFile file : jsonFiles(root, TieredEchoesDataReloader.TEMPLATE_DIRECTORY, report, "template_parse_failure")) {
            if (!TieredEchoesDataReloader.isCompatEligible(file.json())) {
                continue;
            }
            try {
                templates.add(TieredEchoesDataReloader.parseTemplate(file.id(), file.json()));
            } catch (final RuntimeException exception) {
                report.add(failure(file, exception));
            }
        }

        return new Loaded(resolvedTiers, TieredEchoesDataReloader.deduplicateEdges(edges, report), templates, report);
    }

    /** Applies the shipped datapack to the live services, exactly as a server boot would. */
    public static Loaded install() {
        final Loaded loaded = load();
        TierService.getInstance().replaceAll(loaded.tiers());
        PathService.getInstance().replaceAll(loaded.edges());
        TemplateService.getInstance().replaceAll(loaded.templates());
        PolicyService.getInstance().replace(TierPolicy.defaults());
        return loaded;
    }

    /** Restores the state a freshly booted vanilla server would be in. */
    public static void restoreShippedState() {
        install();
    }

    private record JsonFile(ResourceLocation id, JsonObject json, Path path) {
    }

    private static List<JsonFile> jsonFiles(final Path root, final String directory, final ValidationReport report, final String failureType) {
        final Path directoryRoot = root.resolve(directory);
        if (!Files.isDirectory(directoryRoot)) {
            return List.of();
        }
        final List<Path> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(directoryRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(paths::add);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
        final List<JsonFile> files = new ArrayList<>(paths.size());
        for (final Path path : paths) {
            final String relative = directoryRoot.relativize(path).toString().replace('\\', '/');
            final ResourceLocation id = new ResourceLocation(NAMESPACE, directory + "/" + relative);
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                files.add(new JsonFile(id, GsonHelper.parse(reader), path));
            } catch (final Exception exception) {
                report.add(new ValidationIssue(ValidationIssue.Severity.ERROR, failureType,
                        "Failed to parse shipped datapack file",
                        Map.of("resource", id.toString(), "error", String.valueOf(exception.getMessage()))));
            }
        }
        return files;
    }

    private static ValidationIssue failure(final JsonFile file, final RuntimeException exception) {
        return new ValidationIssue(ValidationIssue.Severity.ERROR, "shipped_data_parse_failure",
                "Failed to parse shipped datapack file",
                Map.of("resource", file.id().toString(), "error", String.valueOf(exception.getMessage())));
    }
}
