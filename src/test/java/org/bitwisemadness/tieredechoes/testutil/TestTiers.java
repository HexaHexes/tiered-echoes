package org.bitwisemadness.tieredechoes.testutil;

import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;
import org.bitwisemadness.tieredechoes.common.path.TierEdge;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

/**
 * Small factories for the records the tests build ladders out of.
 *
 * <p>{@link TierDefinition} and {@link TierEdge} each carry around twenty fields that are irrelevant
 * to most assertions; spelling them out inline would bury the interesting part of a test under
 * {@code Optional.empty()} noise.</p>
 */
public final class TestTiers {

    /** Namespace used by every id these factories produce, so they never collide with shipped data. */
    public static final String NAMESPACE = "test";

    private TestTiers() {
    }

    public static ResourceLocation id(final String path) {
        return new ResourceLocation(NAMESPACE, path);
    }

    public static TierDefinition tier(final String path, final TierDomain domain, final int sortOrder) {
        return new TierDefinition(
                id(path),
                domain,
                1,
                Rarity.COMMON,
                sortOrder,
                new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", "stick")),
                Optional.empty(),
                Optional.empty(),
                Optional.of(NAMESPACE),
                Map.of(),
                Map.of(),
                Map.of(),
                true,
                true);
    }

    /**
     * The rung, re-pointed at the named templates rather than at the conventional ones.
     *
     * <p>This is what a fork looks like when it is <em>not</em> self-selecting: two rungs naming one
     * template offer the player no way to tell their branches apart at the table.</p>
     */
    public static TierDefinition naming(final TierDefinition tier, final ResourceLocation upgrade, final ResourceLocation downgrade) {
        return new TierDefinition(
                tier.id(), tier.domain(), tier.schemaVersion(), tier.displayRarity(), tier.sortOrder(),
                tier.upgradeIngredient(), tier.downgradeIngredient(), tier.previousTier(), tier.sourcePack(),
                tier.attributeMultipliers(), tier.curiosSetBonuses(), tier.compatAmplifiers(),
                tier.generatedUpgradeTemplate(), tier.generatedDowngradeTemplate(),
                Optional.of(upgrade), Optional.of(downgrade),
                tier.upgradeTemplateTexture(), tier.downgradeTemplateTexture());
    }

    public static TierEdge edge(final String from, final String to, final TierDomain domain, final int registrationOrder,
                                final Optional<IngredientMatcher> addition, final Optional<ResourceLocation> downgradeTo,
                                final Optional<String> displayBranchId) {
        return new TierEdge(
                id(from),
                id(to),
                domain,
                registrationOrder,
                addition,
                downgradeTo,
                Optional.of(NAMESPACE),
                displayBranchId,
                Map.of());
    }

    public static IngredientMatcher item(final String path) {
        return new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", path));
    }

    public static IngredientMatcher tag(final String path) {
        return new IngredientMatcher(IngredientMatcher.Kind.TAG, new ResourceLocation("minecraft", path));
    }
}
