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
        boolean generatedUpgradeTemplate,
        Optional<ResourceLocation> upgradeTemplateItemModel,
        Optional<ResourceLocation> downgradeTemplateItemModel,
        Optional<ResourceLocation> upgradeTemplateTexture,
        Optional<ResourceLocation> downgradeTemplateTexture
) {
    public TierDefinition {
        downgradeIngredient = downgradeIngredient == null ? Optional.empty() : downgradeIngredient;
        previousTier = previousTier == null ? Optional.empty() : previousTier;
        sourcePack = sourcePack == null ? Optional.empty() : sourcePack;
        upgradeTemplateItemModel = upgradeTemplateItemModel == null ? Optional.empty() : upgradeTemplateItemModel;
        downgradeTemplateItemModel = downgradeTemplateItemModel == null ? Optional.empty() : downgradeTemplateItemModel;
        upgradeTemplateTexture = upgradeTemplateTexture == null ? Optional.empty() : upgradeTemplateTexture;
        downgradeTemplateTexture = downgradeTemplateTexture == null ? Optional.empty() : downgradeTemplateTexture;
        attributeMultipliers = attributeMultipliers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributeMultipliers));
        curiosSetBonuses = curiosSetBonuses == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(curiosSetBonuses));
        compatAmplifiers = compatAmplifiers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(compatAmplifiers));
    }

    public TierDefinition(
            final ResourceLocation id,
            final TierDomain domain,
            final int schemaVersion,
            final Rarity displayRarity,
            final int sortOrder,
            final int experienceRequired,
            final IngredientMatcher upgradeIngredient,
            final Optional<IngredientMatcher> downgradeIngredient,
            final Optional<ResourceLocation> previousTier,
            final Optional<String> sourcePack,
            final Map<String, Double> attributeMultipliers,
            final Map<String, Double> curiosSetBonuses,
            final Map<String, Double> compatAmplifiers,
            final boolean generatedDowngradeTemplate,
            final boolean generatedUpgradeTemplate
    ) {
        this(id, domain, schemaVersion, displayRarity, sortOrder, experienceRequired, upgradeIngredient, downgradeIngredient, previousTier, sourcePack,
                attributeMultipliers, curiosSetBonuses, compatAmplifiers, generatedDowngradeTemplate, generatedUpgradeTemplate,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public TierDefinition withSourcePack(final String pack) {
        return new TierDefinition(this.id, this.domain, this.schemaVersion, this.displayRarity, this.sortOrder, this.experienceRequired,
                this.upgradeIngredient, this.downgradeIngredient, this.previousTier, Optional.ofNullable(pack),
                this.attributeMultipliers, this.curiosSetBonuses, this.compatAmplifiers, this.generatedDowngradeTemplate, this.generatedUpgradeTemplate,
                this.upgradeTemplateItemModel, this.downgradeTemplateItemModel, this.upgradeTemplateTexture, this.downgradeTemplateTexture);
    }
}
