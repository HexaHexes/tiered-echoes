package org.bitwisemadness.tieredechoes.common.tier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record TierDefinition(
        ResourceLocation id,
        TierDomain domain,
        int schemaVersion,
        Rarity displayRarity,
        int sortOrder,
        int experienceRequired,
        IngredientMatcher upgradeIngredient,
        Optional<IngredientMatcher> downgradeIngredient,
        Optional<ResourceLocation> previousTier,
        Optional<String> sourcePack,
        Map<String, Double> attributeMultipliers,
        Map<String, Double> curiosSetBonuses,
        Map<String, Double> compatAmplifiers,
        boolean generatedDowngradeTemplate,
        boolean generatedUpgradeTemplate
) {
    public TierDefinition {
        downgradeIngredient = downgradeIngredient == null ? Optional.empty() : downgradeIngredient;
        previousTier = previousTier == null ? Optional.empty() : previousTier;
        sourcePack = sourcePack == null ? Optional.empty() : sourcePack;
        attributeMultipliers = attributeMultipliers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributeMultipliers));
        curiosSetBonuses = curiosSetBonuses == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(curiosSetBonuses));
        compatAmplifiers = compatAmplifiers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(compatAmplifiers));
    }

    public TierDefinition withSourcePack(final String pack) {
        return new TierDefinition(this.id, this.domain, this.schemaVersion, this.displayRarity, this.sortOrder, this.experienceRequired,
                this.upgradeIngredient, this.downgradeIngredient, this.previousTier, Optional.ofNullable(pack),
                this.attributeMultipliers, this.curiosSetBonuses, this.compatAmplifiers, this.generatedDowngradeTemplate, this.generatedUpgradeTemplate);
    }
}
