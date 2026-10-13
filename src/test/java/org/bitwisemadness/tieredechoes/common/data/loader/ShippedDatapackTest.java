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
import java.util.Optional;
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
    void shipsALadderOfFiveRungsInEachBuiltinDomain() {
        final List<TierDefinition> tiers = ShippedDatapack.load().tiers();

        assertEquals(TierDomain.builtinDomains().size() * 5, tiers.size());
        for (final TierDomain domain : TierDomain.builtinDomains()) {
            final List<TierDefinition> forDomain = tiers.stream().filter(tier -> tier.domain() == domain).toList();
            assertEquals(List.of("common", "uncommon", "rare", "epic", "legendary"),
                    forDomain.stream().map(tier -> tier.id().getPath()).toList(), domain.toString());
        }
    }

    /**
     * The metals that gate each rung are the whole of the progression contract, so a change to them
     * is a change to how long a playthrough takes and must not land by accident.
     */
    @Test
    void eachRungIsGatedByItsMetal() {
        assertEquals(List.of("minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:gold_ingot",
                        "minecraft:diamond", "minecraft:netherite_ingot"),
                ShippedDatapack.load().tiers().stream()
                        .filter(tier -> tier.domain() == TierDomain.WEAPON)
                        .map(tier -> net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(tier.upgradeIngredient().toIngredient().getItems()[0].getItem()).toString())
                        .toList());
    }

    /**
     * The first rung must cost something, or a tierless item climbs the ladder for free.
     *
     * <p>What joining costs is now the smithing addition alone — there is no pool to spend — so the
     * check is that every domain opens with a real material rather than an empty slot. It reads
     * across all built-in domains rather than only the weapon one, which is where the material list
     * above stops.</p>
     */
    @Test
    void theFirstRungOfEveryDomainCostsAMaterialSoNothingJoinsForFree() {
        for (final TierDomain domain : TierDomain.builtinDomains()) {
            final TierDefinition first = ShippedDatapack.load().tiers().stream()
                    .filter(tier -> tier.domain() == domain)
                    .findFirst()
                    .orElseThrow();
            assertTrue(first.upgradeIngredient().toIngredient().getItems().length > 0,
                    domain + "'s first rung takes nothing in the addition slot, so joining it is free");
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

        assertEquals(4, loaded.tiers().stream().filter(tier -> tier.domain() == TierDomain.WEAPON).count(),
                "the weapon ladder should start at uncommon now");
        assertTrue(loaded.tiers().stream().noneMatch(tier -> tier.domain() == TierDomain.WEAPON
                        && tier.id().getPath().equals("common")),
                "no Java fallback should restore the tier the file described");
        assertEquals(5, loaded.tiers().stream().filter(tier -> tier.domain() == TierDomain.ARMOR).count(),
                "the other domains must be untouched by a weapon file going missing");
    }

    @Test
    void shipsFourTransitionsPerDomain() {
        final List<TierEdge> edges = ShippedDatapack.load().edges();

        assertEquals(TierDomain.builtinDomains().size() * 4, edges.size(),
                "every domain of four linear steps: the five rungs joined by four edges");
        for (final TierDomain domain : TierDomain.builtinDomains()) {
            assertEquals(4, edges.stream().filter(edge -> edge.domain() == domain).count(), domain.toString());
        }
    }

    /**
     * Upgrade templates only. The way down is opt-in and the shipped ladder declares no route, so a
     * shipped downgrade template would be an item in the creative tab advertising a transition the
     * smithing table cannot perform. {@code tier_templates} parsing is untouched, so a datapack
     * declaring one still loads — the mod simply does not supply any.
     */
    @Test
    void shipsExactlyOneUpgradeTemplatePerTierAndNoDowngradeTemplate() {
        final List<TemplateDefinition> templates = ShippedDatapack.load().templates();

        assertEquals(5, templates.size(), "five rungs, one upgrade template each");

        final Set<ResourceLocation> ids = new HashSet<>();
        for (final TemplateDefinition template : templates) {
            assertTrue(ids.add(template.id()), "duplicate template id " + template.id());
        }
        for (final String tier : List.of("common", "uncommon", "rare", "epic", "legendary")) {
            assertTrue(ids.contains(new ResourceLocation("tieredechoes", tier + "_upgrade_template")),
                    tier + " upgrade template missing");
        }
    }

    /**
     * The item is the declaration's own name, which is what lets a tier that names no template find
     * it: the id a tier looks up by convention and the item the slot consumes are the same thing.
     */
    @Test
    void everyShippedTemplateSpendsTheItemItIsNamedAfter() {
        for (final TemplateDefinition template : ShippedDatapack.load().templates()) {
            assertEquals(Optional.of(template.id()), template.item(),
                    template.id() + " should declare itself as the item it consumes");
        }
    }

    @Test
    void templateIdsAreScopedToTheTierNotToTheItemTypeCarryingIt() {
        for (final TemplateDefinition template : ShippedDatapack.load().templates()) {
            final String id = template.id().getPath();
            assertFalse(id.contains("weapon") || id.contains("tool") || id.contains("armor") || id.contains("accessory"),
                    template.id() + " is still domain-scoped; one template per tier is the contract");
            assertTrue(id.endsWith("_upgrade_template"), template.id() + " is not an upgrade template");
        }
    }

    /**
     * No shipped step declares where the way down lands, and the {@code downgrades} switch is off
     * by default on top of that.
     *
     * <p>Asserting that directly is the difference between a test that notices a regression and one
     * that quietly accepts a re-enable. The addition assertion is the one that must stay true: a
     * smithing edge with nothing to put in the addition slot cannot generate a recipe, so a
     * datapack could ship an unusable ladder without it.</p>
     */
    @Test
    void everyShippedTransitionHasAnAdditionAndNoneHasADowngradeTarget() {
        for (final TierEdge edge : ShippedDatapack.load().edges()) {
            assertTrue(edge.additionIngredient().isPresent(),
                    edge.from() + " -> " + edge.to() + " has no addition, so its smithing recipe cannot be generated");
            assertFalse(edge.downgradeTo().isPresent(),
                    edge.from() + " -> " + edge.to() + " declares a downgrade target, but the shipped ladder declares no way down");
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
        assertEquals(TierDomain.builtinDomains().size() * 5,
                org.bitwisemadness.tieredechoes.common.tier.TierService.getInstance().getAllTiers().size());
        assertEquals(TierDomain.builtinDomains().size() * 4,
                org.bitwisemadness.tieredechoes.common.path.PathService.getInstance().getAllEdgesLike().size());
        // Templates are named entities rather than per-domain ones, so their count tracks the number
        // of rungs in a ladder and not the number of domains. Only upgrades ship.
        assertEquals(5, org.bitwisemadness.tieredechoes.common.template.TemplateService.getInstance()
                .declaredTemplates().size());
    }

    @Test
    void templateIdsAreUniqueSoNeitherLookupCanBeShadowed() {
        final Set<ResourceLocation> ids = new HashSet<>();
        for (final TemplateDefinition template : ShippedDatapack.load().templates()) {
            assertTrue(ids.add(template.id()), "duplicate template id " + template.id());
        }
        assertEquals(5, ids.size());
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
