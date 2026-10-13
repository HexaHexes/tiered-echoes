package org.bitwisemadness.tieredechoes.common.tier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One rung of one ladder, as {@code tier_policies} declared it.
 *
 * <p>The tier says what an item <em>is</em> once it reaches this rung — which attributes it scales
 * and how far. It says nothing about how an item gets here: that is the job of the edges leaving
 * and arriving at it, which keeps a fork describable without the rung having to know how many
 * routes lead to it.</p>
 *
 * <p>Templates are now reusable named entities shared across domains. A tier declares which
 * upgrade and downgrade templates it uses by name; the template's item and properties are
 * independent of the domain. Templates that are not declared default to the built-in
 * {@code <tier>_upgrade_template} / {@code <tier>_downgrade_template} items.</p>
 *
 * @param id                    the rung's id, unique within its domain
 * @param domain                the ladder this rung belongs to
 * @param schemaVersion         the version of the {@code tier_policies} shape that declared it
 * @param displayRarity         the colour the rung renders with
 * @param sortOrder             the rung's position among its siblings, ascending
 * @param upgradeIngredient     the smithing addition for the route out of this rung
 * @param downgradeIngredient   the smithing addition for the route back down, if one was declared
 * @param previousTier          the rung directly below, for ladders that are a plain chain
 * @param sourcePack            the pack that declared the rung, for validation messages
 * @param attributeMultipliers  attribute name to multiplier, applied to a tiered item
 * @param curiosSetBonuses      set-bonus scaling for the Curios/Apaccessories family
 * @param compatAmplifiers      per-mod scaling
 * @param generatedUpgradeTemplate whether the upgrade template item was auto-generated
 * @param generatedDowngradeTemplate whether the downgrade template item was auto-generated
 * @param upgradeTemplateId     the template this rung names for the way up, by template id
 * @param downgradeTemplateId   the template this rung names for the way down, by template id
 * @param upgradeTemplateTexture an optional override for the upgrade template's texture
 * @param downgradeTemplateTexture an optional override for the downgrade template's texture
 */
public record TierDefinition(
        ResourceLocation id,
        TierDomain domain,
        int schemaVersion,
        Rarity displayRarity,
        int sortOrder,
        IngredientMatcher upgradeIngredient,
        Optional<IngredientMatcher> downgradeIngredient,
        Optional<ResourceLocation> previousTier,
        Optional<String> sourcePack,
        Map<String, Double> attributeMultipliers,
        Map<String, Double> curiosSetBonuses,
        Map<String, Double> compatAmplifiers,
        boolean generatedUpgradeTemplate,
        boolean generatedDowngradeTemplate,
        Optional<ResourceLocation> upgradeTemplateId,
        Optional<ResourceLocation> downgradeTemplateId,
        Optional<ResourceLocation> upgradeTemplateTexture,
        Optional<ResourceLocation> downgradeTemplateTexture
) {
    public TierDefinition {
        downgradeIngredient = downgradeIngredient == null ? Optional.empty() : downgradeIngredient;
        previousTier = previousTier == null ? Optional.empty() : previousTier;
        sourcePack = sourcePack == null ? Optional.empty() : sourcePack;
        upgradeTemplateId = upgradeTemplateId == null ? Optional.empty() : upgradeTemplateId;
        downgradeTemplateId = downgradeTemplateId == null ? Optional.empty() : downgradeTemplateId;
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
            final IngredientMatcher upgradeIngredient,
            final Optional<IngredientMatcher> downgradeIngredient,
            final Optional<ResourceLocation> previousTier,
            final Optional<String> sourcePack,
            final Map<String, Double> attributeMultipliers,
            final Map<String, Double> curiosSetBonuses,
            final Map<String, Double> compatAmplifiers,
            final boolean generatedUpgradeTemplate,
            final boolean generatedDowngradeTemplate
    ) {
        this(id, domain, schemaVersion, displayRarity, sortOrder, upgradeIngredient, downgradeIngredient, previousTier, sourcePack,
                attributeMultipliers, curiosSetBonuses, compatAmplifiers, generatedUpgradeTemplate, generatedDowngradeTemplate,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public TierDefinition withSourcePack(final String pack) {
        return new TierDefinition(this.id, this.domain, this.schemaVersion, this.displayRarity, this.sortOrder,
                this.upgradeIngredient, this.downgradeIngredient, this.previousTier, Optional.ofNullable(pack),
                this.attributeMultipliers, this.curiosSetBonuses, this.compatAmplifiers, this.generatedUpgradeTemplate, this.generatedDowngradeTemplate,
                this.upgradeTemplateId, this.downgradeTemplateId, this.upgradeTemplateTexture, this.downgradeTemplateTexture);
    }
}