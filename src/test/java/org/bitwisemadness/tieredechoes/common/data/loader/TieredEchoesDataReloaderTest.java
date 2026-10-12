package org.bitwisemadness.tieredechoes.common.data.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationReport;
import org.bitwisemadness.tieredechoes.common.path.TierEdge;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.template.TemplateDefinition;
import org.bitwisemadness.tieredechoes.testutil.TestTiers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JSON contract, exercised one helper at a time.
 *
 * <p>These live in the reloader's own package because that is the only way to reach its
 * package-private parse helpers without widening production visibility for the sake of tests.</p>
 */
class TieredEchoesDataReloaderTest {

    private static final ResourceLocation SOURCE = new ResourceLocation("testpack", "tiers/example.json");

    private static JsonObject json(final String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static TierDefinition weapon(final String path) {
        return TestTiers.tier(path, TierDomain.WEAPON, 0);
    }

    /**
     * An edge in the shipped namespace with a caller-chosen {@code sourcePack}, so tests can stage
     * the builtin-vs-datapack ordering the real loader produces.
     */
    private static TierEdge tieredEchoesEdge(final String sourcePack, final String from, final String to,
                                             final int registrationOrder, final String displayBranchId) {
        return new TierEdge(
                new ResourceLocation("tieredechoes", from),
                new ResourceLocation("tieredechoes", to),
                TierDomain.WEAPON,
                registrationOrder,
                Optional.empty(),
                Optional.of(new ResourceLocation("tieredechoes", from)),
                Optional.of(sourcePack),
                Optional.of(displayBranchId),
                Map.of());
    }

    // ---------------------------------------------------------------- templates

    /**
     * The template files were collapsed to one upgrade and one downgrade per tier. A datapack
     * written against the older per-domain layout still carries a {@code domain} key, and reading it
     * back as an error would break every one of those packs for no benefit.
     */
    @Test
    void templateParsingIgnoresALegacyDomainKey() {
        final TemplateDefinition template = TieredEchoesDataReloader.parseTemplate(SOURCE, json("""
                {
                  "id": "tieredechoes:rare_upgrade_template",
                  "tier": "tieredechoes:rare",
                  "domain": "weapon",
                  "kind": "upgrade",
                  "item": "tieredechoes:rare_upgrade_template"
                }
                """));

        assertEquals(new ResourceLocation("tieredechoes", "rare"), template.tier());
        assertFalse(template.downgrade());
        assertEquals(Optional.of(new ResourceLocation("tieredechoes", "rare_upgrade_template")), template.item());
    }

    @Test
    void templateKindDefaultsToUpgradeAndAcceptsBothSpellingsCaseInsensitively() {
        final TemplateDefinition implicit = TieredEchoesDataReloader.parseTemplate(SOURCE, json("""
                {"tier":"tieredechoes:common"}
                """));
        final TemplateDefinition shouted = TieredEchoesDataReloader.parseTemplate(SOURCE, json("""
                {"tier":"tieredechoes:common","kind":"DOWNGRADE"}
                """));

        assertFalse(implicit.downgrade());
        assertTrue(shouted.downgrade());
    }

    @Test
    void templateIdFallsBackToTheResourceItCameFrom() {
        final TemplateDefinition template = TieredEchoesDataReloader.parseTemplate(SOURCE, json("""
                {"tier":"tieredechoes:common"}
                """));

        assertEquals(SOURCE, template.id());
    }

    @Test
    void templateKindIsAllowedToBeWrongOnlyIfItIsRejectedLoudly() {
        assertThrows(IllegalArgumentException.class, () -> TieredEchoesDataReloader.parseTemplate(SOURCE, json("""
                {"tier":"tieredechoes:common","kind":"sideways"}
                """)));
    }

    // ---------------------------------------------------------------- edges

    @Test
    void edgeParsingReadsEveryFieldItSupports() {
        final TierEdge edge = TieredEchoesDataReloader.parseEdge(json("""
                {
                  "from": "tieredechoes:common",
                  "to": "tieredechoes:uncommon",
                  "addition": {"item":"minecraft:iron_ingot"},
                  "downgrade_to": "tieredechoes:common",
                  "display_branch_id": "my_branch",
                  "compat_amplifiers": {"apotheosis:affix": 2.0}
                }
                """), TierDomain.WEAPON, 42, "testpack", new ValidationReport());

        assertEquals(new ResourceLocation("tieredechoes", "common"), edge.from());
        assertEquals(new ResourceLocation("tieredechoes", "uncommon"), edge.to());
        assertEquals(TierDomain.WEAPON, edge.domain());
        assertEquals(42, edge.registrationOrder());
        assertEquals(IngredientMatcher.Kind.ITEM, edge.additionIngredient().orElseThrow().kind());
        assertEquals(Optional.of(new ResourceLocation("tieredechoes", "common")), edge.downgradeTo());
        assertEquals(Optional.of("my_branch"), edge.displayBranchId());
        assertEquals(2.0D, edge.compatAmplifiers().get("apotheosis:affix"));
        assertEquals(Optional.of("testpack"), edge.sourcePack());
    }

    /**
     * {@code attunement_ingredient} spelled the smithing addition before attunement was removed.
     *
     * <p>Read as a warning rather than silently dropped: a pack still carrying the old key would
     * otherwise publish recipes with an empty addition slot and give no clue why. It is not read as
     * an alias either — accepting both spellings forever is how a schema stops being able to
     * change.</p>
     */
    @Test
    void theRemovedAttunementIngredientKeyIsReportedRatherThanReadAsAnAlias() {
        final ValidationReport report = new ValidationReport();

        final TierEdge edge = TieredEchoesDataReloader.parseEdge(json("""
                {"from":"tieredechoes:common","to":"tieredechoes:uncommon","attunement_ingredient":{"item":"minecraft:iron_ingot"}}
                """), TierDomain.WEAPON, 1, "testpack", report);

        assertTrue(edge.additionIngredient().isEmpty(),
                "the removed key must not be read back under a name it never had");
        assertTrue(report.issues().stream().anyMatch(issue -> issue.type().equals("removed_policy_field")),
                "a pack still using the old spelling should be told what to change");
        assertEquals(0, report.errorCount(), "a stale key should not stop the datapack loading");
    }

    @Test
    void aBranchWithNoExplicitNameGetsADerivedOne() {
        final TierEdge edge = TieredEchoesDataReloader.parseEdge(json("""
                {"from":"tieredechoes:common","to":"tieredechoes:uncommon"}
                """), TierDomain.WEAPON, 7, "testpack", new ValidationReport());

        assertTrue(edge.displayBranchId().isPresent());
        assertFalse(edge.displayBranchId().orElseThrow().isBlank());
    }

    // ---------------------------------------------------------------- domain resolution

    @Test
    void anEdgeLevelDomainWinsOverTheFileLevelOne() {
        final TierDomain resolved = TieredEchoesDataReloader.resolveEdgeDomain(
                json("""
                        {"domain":"armor","from":"tieredechoes:common","to":"tieredechoes:uncommon"}
                        """),
                Optional.of(TierDomain.WEAPON), List.of(), new ValidationReport(), SOURCE, 1);

        assertEquals(TierDomain.ARMOR, resolved);
    }

    @Test
    void theFileLevelDomainIsUsedWhenTheEdgeDoesNotOverrideIt() {
        final TierDomain resolved = TieredEchoesDataReloader.resolveEdgeDomain(
                json("""
                        {"from":"tieredechoes:common","to":"tieredechoes:uncommon"}
                        """),
                Optional.of(TierDomain.TOOL), List.of(), new ValidationReport(), SOURCE, 1);

        assertEquals(TierDomain.TOOL, resolved);
    }

    @Test
    void withNoDeclarationTheDomainIsInferredFromTheTierThatOwnsTheSource() {
        final TierDomain resolved = TieredEchoesDataReloader.resolveEdgeDomain(
                json("""
                        {"from":"test:alpha","to":"test:beta"}
                        """),
                Optional.empty(), List.of(TestTiers.tier("alpha", TierDomain.ARMOR, 0)),
                new ValidationReport(), SOURCE, 1);

        assertEquals(TierDomain.ARMOR, resolved);
    }

    @Test
    void inferenceRefusesToGuessWhenTheSourceTierExistsInTwoDomains() {
        final ValidationReport report = new ValidationReport();
        final TierDomain resolved = TieredEchoesDataReloader.resolveEdgeDomain(
                json("""
                        {"from":"test:alpha","to":"test:beta"}
                        """),
                Optional.empty(),
                List.of(TestTiers.tier("alpha", TierDomain.ARMOR, 0),
                        TestTiers.tier("alpha", TierDomain.WEAPON, 0)),
                report, SOURCE, 1);

        assertNull(resolved);
        assertTrue(report.issues().stream().anyMatch(issue -> issue.type().equals("path_domain_unresolvable")));
        assertTrue(report.errorCount() > 0);
    }

    @Test
    void inferenceRefusesToGuessWhenNoDomainDeclaresTheSourceTier() {
        final ValidationReport report = new ValidationReport();
        final TierDomain resolved = TieredEchoesDataReloader.resolveEdgeDomain(
                json("""
                        {"from":"test:alpha","to":"test:beta"}
                        """),
                Optional.empty(), List.of(weapon("other")), report, SOURCE, 1);

        assertNull(resolved);
        assertTrue(report.issues().stream().anyMatch(issue -> issue.type().equals("path_domain_unresolvable")));
    }

    @Test
    void anEdgeWithNeitherADomainNorASourceTierIsReportedAsMissingFrom() {
        final ValidationReport report = new ValidationReport();
        final TierDomain resolved = TieredEchoesDataReloader.resolveEdgeDomain(
                json("""
                        {"to":"test:beta"}
                        """),
                Optional.empty(), List.of(), report, SOURCE, 1);

        assertNull(resolved);
        assertTrue(report.issues().stream().anyMatch(issue -> issue.type().equals("path_missing_from")));
    }

    @Test
    void anUnknownDomainNameIsRejectedRatherThanSilentlyIgnored() {
        assertThrows(IllegalArgumentException.class, () -> TieredEchoesDataReloader.optionalDomain(
                json("""
                        {"domain":"swords"}
                        """), "domain"));
        assertEquals(Optional.empty(), TieredEchoesDataReloader.optionalDomain(json("{}"), "domain"));
    }

    // ---------------------------------------------------------------- policy

    @Test
    void aPartialPolicyMergesOntoTheDefaults() {
        final ValidationReport report = new ValidationReport();

        final TierPolicy merged = TieredEchoesDataReloader.mergePolicy(TierPolicy.defaults(), json("""
                {"tier_upgrades":false}
                """), report);

        assertFalse(merged.tierUpgrades(), "the stated switch must be adopted");
        assertTrue(merged.tierSystem(), "the unstated ones must keep the default");
        assertTrue(merged.levellingSystem());
        assertTrue(merged.levelUp());
        assertTrue(merged.downgrades());
        assertEquals(0, report.issues().size(), "a modern policy file should produce no findings");
    }

    /** The legacy key keeps meaning what it meant, so an old pack still closes the route. */
    @Test
    void disableUpgradeRecipesStillClosesTheRouteButIsReportedAsSuperseded() {
        final ValidationReport report = new ValidationReport();

        final TierPolicy merged = TieredEchoesDataReloader.mergePolicy(TierPolicy.defaults(), json("""
                {"disable_upgrade_recipes":true}
                """), report);

        assertFalse(merged.tierUpgrades(), "the alias must keep its old meaning");
        assertTrue(report.issues().stream().anyMatch(issue -> issue.type().equals("superseded_policy_field")),
                "the pack should be told which key replaces it");
        assertEquals(0, report.errorCount(), "an alias is a migration, not a failure");
    }

    /** AUTO_ONLY used to hand progression to experience, which no longer drives anything. */
    @Test
    void autoOnlyStillClosesSmithingEvenThoughExperienceNoLongerDrivesProgression() {
        final ValidationReport report = new ValidationReport();

        final TierPolicy merged = TieredEchoesDataReloader.mergePolicy(TierPolicy.defaults(), json("""
                {"mode":"AUTO_ONLY"}
                """), report);

        assertFalse(merged.tierUpgrades(),
                "the surviving half of AUTO_ONLY's intent is that the smithing route is shut");
        assertTrue(merged.tierSystem(), "the mode never claimed tiers should not exist");
        assertTrue(report.issues().stream().anyMatch(issue -> issue.type().equals("superseded_policy_field")));
    }

    /** HYBRID and SMITHING_ONLY both meant the smithing route is open, which is already the default. */
    @Test
    void theModesThatKeptSmithingOpenLeaveItOpen() {
        for (final String mode : List.of("SMITHING_ONLY", "HYBRID")) {
            final TierPolicy merged = TieredEchoesDataReloader.mergePolicy(TierPolicy.defaults(), json("""
                    {"mode":"%s"}
                    """.formatted(mode)), new ValidationReport());

            assertTrue(merged.tierUpgrades(), mode + " never meant the route was closed");
        }
    }

    /**
     * Adding the new key takes effect before the old one is deleted, not after — otherwise a pack
     * mid-migration would find its new declaration ignored by a key it is about to remove.
     */
    @Test
    void theModernKeyGovernsWhenBothSpellingsAppearInOneFile() {
        final ValidationReport report = new ValidationReport();

        final TierPolicy merged = TieredEchoesDataReloader.mergePolicy(TierPolicy.defaults(), json("""
                {"tier_upgrades":true,"disable_upgrade_recipes":true}
                """), report);

        assertTrue(merged.tierUpgrades(), "the modern key must win");
        assertTrue(report.issues().stream().anyMatch(issue -> issue.type().equals("superseded_policy_field")),
                "the stale key must still be reported, or it would never be deleted");
    }

    @Test
    void fieldsThatDescribedItemExperienceAreIgnoredRatherThanFatal() {
        for (final String key : List.of("multi_path_resolution", "carry_over_excess_xp", "consume_attunement_item")) {
            final ValidationReport report = new ValidationReport();

            final TierPolicy merged = TieredEchoesDataReloader.mergePolicy(TierPolicy.defaults(), json("""
                    {"%s":false}
                    """.formatted(key)), report);

            assertEquals(TierPolicy.defaults(), merged,
                    key + " should be parsed for compatibility and then dropped");
            assertTrue(report.issues().stream().anyMatch(issue -> issue.type().equals("obsolete_policy_field")),
                    key + " should be reported as obsolete");
        }
    }

    /**
     * {@code mode} used to be an enum with an {@code IllegalArgumentException} waiting for a typo.
     *
     * <p>It is now an inert legacy key, so an unrecognised value has nothing to collide with — and
     * that is the point of removing the enum rather than growing it. The file still loads, and the
     * key is still reported.</p>
     */
    @Test
    void anUnrecognisedLegacyModeDoesNotStopTheFileLoading() {
        final ValidationReport report = new ValidationReport();

        final TierPolicy merged = TieredEchoesDataReloader.mergePolicy(TierPolicy.defaults(), json("""
                {"mode":"sideways"}
                """), report);

        assertEquals(TierPolicy.defaults(), merged, "an unrecognised mode has no mapping to make");
        assertTrue(report.issues().stream().anyMatch(issue -> issue.type().equals("superseded_policy_field")),
                "the key should still be reported as superseded");
        assertEquals(0, report.errorCount(), "a legacy typo must not be an error");
    }

    // ---------------------------------------------------------------- de-duplication

    /**
     * Ladder order is what {@code sort_order} declares, not the order the files came off disk in.
     * A directory walk is alphabetical, which would otherwise put epic before uncommon.
     */
    @Test
    void ladderOrderFollowsSortOrderRatherThanFileEnumerationOrder() {
        final ValidationReport report = new ValidationReport();

        final List<TierDefinition> merged = TieredEchoesDataReloader.deduplicateTiers(
                List.of(
                        TestTiers.tier("epic", TierDomain.WEAPON, 3),
                        TestTiers.tier("common", TierDomain.ARMOR, 0),
                        TestTiers.tier("rare", TierDomain.WEAPON, 2),
                        TestTiers.tier("uncommon", TierDomain.WEAPON, 1),
                        TestTiers.tier("epic", TierDomain.ARMOR, 3),
                        TestTiers.tier("common", TierDomain.WEAPON, 0)),
                report);

        assertEquals(
                List.of("common", "uncommon", "rare", "epic", "common", "epic"),
                merged.stream().map(tier -> tier.id().getPath()).toList(),
                "domains stay grouped, and each domain runs common to epic");
        assertEquals(List.of(TierDomain.WEAPON, TierDomain.WEAPON, TierDomain.WEAPON, TierDomain.WEAPON,
                        TierDomain.ARMOR, TierDomain.ARMOR),
                merged.stream().map(TierDefinition::domain).toList());
    }

    @Test
    void twoDatapacksFightingOverTheSameIdWarn() {
        final ValidationReport report = new ValidationReport();
        final TierDefinition first = TestTiers.tier("common", TierDomain.WEAPON, 0);
        // Marked with its own pack so "the later one wins" can be observed on a field that differs;
        // two records identical in every field would say nothing about which one survived.
        final TierDefinition second = TestTiers.tier("common", TierDomain.WEAPON, 0).withSourcePack("secondpack");

        final List<TierDefinition> merged = TieredEchoesDataReloader.deduplicateTiers(
                List.of(first, second), report);

        assertEquals(1, merged.size());
        assertEquals(Optional.of("secondpack"), merged.get(0).sourcePack(), "the later declaration wins");
        assertTrue(report.issues().stream().anyMatch(issue -> issue.type().equals("duplicate_tier_definition")));
    }

    @Test
    void identicalDuplicateTiersAreNotReportedAsAClash() {
        final ValidationReport report = new ValidationReport();
        final TierDefinition tier = TestTiers.tier("common", TierDomain.WEAPON, 0);

        TieredEchoesDataReloader.deduplicateTiers(List.of(tier, tier), report);

        assertEquals(0, report.warningCount());
    }

    /** Two branches out of the same tier are the point of branching and must both survive. */
    @Test
    void distinctBranchesOutOfOneSourceAreKeptWhileARepeatedTransitionIsDropped() {
        final ValidationReport report = new ValidationReport();
        // Two packs describing the same transition is a genuine clash, so the later one wins and a
        // warning is raised - unlike a re-statement of an identical definition, which stays quiet.
        final TierEdge earlier = tieredEchoesEdge("firstpack", "common", "uncommon", 1, "a");
        final TierEdge repeated = tieredEchoesEdge("secondpack", "common", "uncommon", 2, "b");
        final TierEdge toRare = tieredEchoesEdge("secondpack", "common", "rare", 3, "c");

        final List<TierEdge> merged = TieredEchoesDataReloader.deduplicateEdges(
                List.of(earlier, repeated, toRare), report);

        assertEquals(2, merged.size());
        assertEquals("c", merged.get(1).displayBranchId().orElseThrow(),
                "the distinct branch must survive alongside the deduplicated one");
        assertTrue(report.issues().stream().anyMatch(issue -> issue.type().equals("duplicate_tier_path")));
    }

    /** Branch ids are how two routes out of one tier are told apart, so a duplicate is a real clash. */
    @Test
    void twoEdgesSharingADisplayBranchIdWouldBeReportedByValidationRatherThanHere() {
        final ValidationReport report = new ValidationReport();
        final TierEdge first = tieredEchoesEdge("datapack", "common", "uncommon", 1, "same_id");
        final TierEdge second = tieredEchoesEdge("datapack", "common", "rare", 2, "same_id");

        final List<TierEdge> merged = TieredEchoesDataReloader.deduplicateEdges(List.of(first, second), report);

        assertEquals(2, merged.size(), "distinct transitions are not duplicates");
        assertEquals(0, report.warningCount(),
                "deduplication keys on the transition; the branch-id clash belongs to PathService.validate");
    }

    @Test
    void twoDatapacksFightingOverOneTransitionWarn() {
        final ValidationReport report = new ValidationReport();
        final TierEdge first = TestTiers.edge("common", "uncommon", TierDomain.WEAPON, 1,
                Optional.empty(), Optional.empty(), Optional.of("a"));
        final TierEdge second = TestTiers.edge("common", "uncommon", TierDomain.WEAPON, 2,
                Optional.empty(), Optional.empty(), Optional.of("a"));

        final List<TierEdge> merged = TieredEchoesDataReloader.deduplicateEdges(List.of(first, second), report);

        assertEquals(1, merged.size());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.type().equals("duplicate_tier_path")));
    }

    // ---------------------------------------------------------------- compat gate

    @Test
    void filesWithoutARequiresModListAreAlwaysEligible() {
        assertTrue(TieredEchoesDataReloader.isCompatEligible(json("{}")));
        assertTrue(TieredEchoesDataReloader.isCompatEligible(json("""
                {"requires_mods":[]}
                """)));
    }

    // ---------------------------------------------------------------- tiers

    @Test
    void tierParsingReadsEveryFieldItSupports() {
        final TierDefinition tier = TieredEchoesDataReloader.parseTier(SOURCE, json("""
                {
                  "id":"test:alpha",
                  "domain":"weapon",
                  "schema_version":4,
                  "display_rarity":"RARE",
                  "sort_order":2,
                  "upgrade_ingredient":{"item":"minecraft:diamond"},
                  "downgrade_ingredient":{"tag":"minecraft:ingots"},
                  "previous_tier":"test:beta",
                  "attribute_multipliers":{"attack_damage":1.25},
                  "curios_set_bonuses":{"*3":1.15},
                  "compat_amplifiers":{"apotheosis:affix":2.0},
                  "generated_upgrade_template":false,
                  "generated_downgrade_template":true,
                  "upgrade_template_item_model":"tieredechoes:models/item/x",
                  "downgrade_template_texture":"tieredechoes:textures/item/y"
                }
                """));

        assertEquals(TestTiers.id("alpha"), tier.id());
        assertEquals(TierDomain.WEAPON, tier.domain());
        assertEquals(4, tier.schemaVersion());
        assertEquals(net.minecraft.world.item.Rarity.RARE, tier.displayRarity());
        assertEquals(2, tier.sortOrder());
        assertEquals(IngredientMatcher.Kind.ITEM, tier.upgradeIngredient().kind());
        assertEquals(IngredientMatcher.Kind.TAG, tier.downgradeIngredient().orElseThrow().kind());
        assertEquals(Optional.of(TestTiers.id("beta")), tier.previousTier());
        assertEquals(1.25D, tier.attributeMultipliers().get("attack_damage"));
        assertEquals(1.15D, tier.curiosSetBonuses().get("*3"));
        assertEquals(2.0D, tier.compatAmplifiers().get("apotheosis:affix"));
        assertFalse(tier.generatedUpgradeTemplate());
        assertTrue(tier.generatedDowngradeTemplate());
        assertTrue(tier.upgradeTemplateId().isPresent());
        assertTrue(tier.downgradeTemplateTexture().isPresent());
        assertEquals(Optional.of("testpack"), tier.sourcePack());
    }

    @Test
    void tierOptionalBlocksDefaultRatherThanNpeing() {
        final TierDefinition tier = TieredEchoesDataReloader.parseTier(SOURCE, json("""
                {"id":"test:alpha","domain":"tool","upgrade_ingredient":{"item":"minecraft:stick"}}
                """));

        assertEquals(1, tier.schemaVersion());
        assertEquals(net.minecraft.world.item.Rarity.COMMON, tier.displayRarity());
        assertEquals(0, tier.sortOrder());
        assertTrue(tier.downgradeIngredient().isEmpty());
        assertTrue(tier.previousTier().isEmpty());
        assertTrue(tier.attributeMultipliers().isEmpty());
        assertTrue(tier.generatedUpgradeTemplate());
        assertTrue(tier.generatedDowngradeTemplate());
    }

    @Test
    void aTierWithoutAnUpgradeIngredientIsRejected() {
        assertThrows(JsonParseException.class, () -> TieredEchoesDataReloader.parseTier(SOURCE, json("""
                {"id":"test:alpha","domain":"tool"}
                """)));
    }
}
