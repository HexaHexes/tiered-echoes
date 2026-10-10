package org.bitwisemadness.tieredechoes.common.data.loader;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationIssue;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationReport;
import org.bitwisemadness.tieredechoes.common.path.TierEdge;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.template.TemplateDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The datapack that ships in {@code src/main/resources} is the mod's public data contract: a broken
 * file here breaks every fresh install, and the server console is the only place a player would
 * ever see the reason.
 *
 * <p>These assertions replace the manual "boot a dedicated server and read the validation line"
 * check, so a schema drift fails the build instead of the next release.</p>
 */
class ShippedDatapackTest {

    @AfterEach
    void tearDown() {
        ShippedDatapack.restoreShippedState();
    }

    @Test
    void shippedDataParsesWithoutAnyValidationFinding() {
        final ValidationReport report = ShippedDatapack.load().report();

        assertEquals(0, report.errorCount(), () -> "errors:\n" + describe(report));
        assertEquals(0, report.warningCount(), () -> "warnings:\n" + describe(report));
    }

    @Test
    void shipsALadderOfFourTiersInEachOfTheFourBuiltinDomains() {
        final List<TierDefinition> tiers = ShippedDatapack.load().tiers();

        assertEquals(16, tiers.size());
        for (final TierDomain domain : TierDomain.builtinDomains()) {
            final List<TierDefinition> forDomain = tiers.stream().filter(tier -> tier.domain() == domain).toList();
            assertEquals(List.of("common", "uncommon", "rare", "epic"),
                    forDomain.stream().map(tier -> tier.id().getPath()).toList(), domain.toString());
        }
    }

    /**
     * The point of dropping the Java built-in ladder: a tier exists because a file says so. Without
     * a fallback there is nothing to quietly put it back, so a pack that does not ship the file does
     * not get the tier - which is what makes the shipped JSON a real contract rather than a copy of
     * a table held in code.
     */
    @Test
    void aPackThatOmitsATierFileLosesThatTierRatherThanFallingBackToAJavaTable(@TempDir final Path pack) throws IOException {
        copyRecursively(ShippedDatapack.dataRoot(), pack);
        Files.delete(pack.resolve("tiers").resolve("weapon").resolve("common.json"));

        final ShippedDatapack.Loaded loaded = ShippedDatapack.loadFrom(pack);

        assertEquals(3, loaded.tiers().stream().filter(tier -> tier.domain() == TierDomain.WEAPON).count(),
                "the weapon ladder should start at uncommon now");
        assertTrue(loaded.tiers().stream().noneMatch(tier -> tier.domain() == TierDomain.WEAPON
                        && tier.id().getPath().equals("common")),
                "no Java fallback should restore the tier the file described");
        assertEquals(4, loaded.tiers().stream().filter(tier -> tier.domain() == TierDomain.ARMOR).count(),
                "the other domains must be untouched by a weapon file going missing");
    }

    @Test
    void shipsThreeTransitionsPerDomain() {
        final List<TierEdge> edges = ShippedDatapack.load().edges();

        assertEquals(12, edges.size(),
                "four domains of three linear steps: common to uncommon, uncommon to rare, rare to epic");
        for (final TierDomain domain : TierDomain.builtinDomains()) {
            assertEquals(3, edges.stream().filter(edge -> edge.domain() == domain).count(), domain.toString());
        }
    }

    /** The whole point of the collapse: one pair per tier, shared by every item type. */
    @Test
    void shipsExactlyOneUpgradeAndOneDowngradeTemplatePerTier() {
        final List<TemplateDefinition> templates = ShippedDatapack.load().templates();

        assertEquals(8, templates.size(), "four tiers, two templates each");

        final Set<String> keys = new HashSet<>();
        for (final TemplateDefinition template : templates) {
            assertTrue(keys.add(template.tier() + "|" + template.downgrade()),
                    "duplicate (tier, downgrade) declaration for " + template.tier());
        }
        for (final String tier : List.of("common", "uncommon", "rare", "epic")) {
            assertTrue(keys.contains("tieredechoes:" + tier + "|false"), tier + " upgrade template missing");
            assertTrue(keys.contains("tieredechoes:" + tier + "|true"), tier + " downgrade template missing");
        }
    }

    @Test
    void templateIdsAreScopedToTheTierNotToTheItemTypeCarryingIt() {
        for (final TemplateDefinition template : ShippedDatapack.load().templates()) {
            final String id = template.id().getPath();
            assertFalse(id.contains("weapon") || id.contains("tool") || id.contains("armor") || id.contains("accessory"),
                    template.id() + " is still domain-scoped; one template per tier is the contract");
            assertTrue(id.startsWith(template.tier().getPath() + "_"),
                    template.id() + " should be named after its tier");
        }
    }

    @Test
    void everyShippedTransitionCanBeUndone() {
        for (final TierEdge edge : ShippedDatapack.load().edges()) {
            assertTrue(edge.downgradeTo().isPresent(), edge.from() + " -> " + edge.to() + " has no downgrade target");
            assertTrue(edge.attunementIngredient().isPresent(),
                    edge.from() + " -> " + edge.to() + " has no addition, so its smithing recipe cannot be generated");
        }
    }

    @Test
    void branchIdsAreUniqueAcrossTheWholeGraph() {
        final List<TierEdge> edges = ShippedDatapack.load().edges();
        final Set<String> branchIds = new HashSet<>();

        for (final TierEdge edge : edges) {
            assertTrue(branchIds.add(edge.displayBranchId().orElse(edge.from() + "->" + edge.to())),
                    "branch id collision between two edges");
        }
        assertEquals(edges.size(), branchIds.size());
    }

    @Test
    void installingTheShippedDataProducesACleanServerState() {
        ShippedDatapack.install();

        final ValidationReport report = org.bitwisemadness.tieredechoes.common.path.PathService
                .getInstance()
                .validate(org.bitwisemadness.tieredechoes.common.tier.TierService.getInstance());

        assertEquals(0, report.errorCount(), () -> "errors:\n" + describe(report));
        assertEquals(0, report.warningCount(), () -> "warnings:\n" + describe(report));
        assertEquals(16, org.bitwisemadness.tieredechoes.common.tier.TierService.getInstance().getAllTiers().size());
        assertEquals(12, org.bitwisemadness.tieredechoes.common.path.PathService.getInstance().getAllEdgesLike().size());
        assertEquals(8, org.bitwisemadness.tieredechoes.common.template.TemplateService.getInstance()
                .declaredTemplates().size());
    }

    @Test
    void templateIdsAreUniqueSoNeitherLookupCanBeShadowed() {
        final Set<ResourceLocation> ids = new HashSet<>();
        for (final TemplateDefinition template : ShippedDatapack.load().templates()) {
            assertTrue(ids.add(template.id()), "duplicate template id " + template.id());
        }
        assertEquals(8, ids.size());
    }

    /** Copies a pack root so a test can delete or override a file without touching the real one. */
    private static void copyRecursively(final Path from, final Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (final Path source : walk.toList()) {
                final Path destination = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String describe(final ValidationReport report) {
        final StringBuilder builder = new StringBuilder();
        for (final ValidationIssue issue : report.issues()) {
            builder.append('\n').append(issue.severity()).append(' ').append(issue.type())
                    .append(" — ").append(issue.message()).append(' ').append(issue.context());
        }
        return builder.toString();
    }
}
