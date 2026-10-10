package org.bitwisemadness.tieredechoes.common.path;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.util.Map;
import java.util.Optional;

public record TierEdge(
        ResourceLocation from,
        ResourceLocation to,
        TierDomain domain,
        int registrationOrder,
        Optional<IngredientMatcher> attunementIngredient,
        Optional<ResourceLocation> downgradeTo,
        Optional<String> sourcePack,
        Optional<Integer> experienceRequirementOverride,
        Optional<IngredientMatcher> upgradeIngredientOverride,
        Optional<String> displayBranchId,
        Map<String, Double> compatAmplifiers
) {
    public TierEdge {
        if (domain == null) {
            throw new IllegalArgumentException("TierEdge requires a domain: " + from + " -> " + to);
        }
        attunementIngredient = attunementIngredient == null ? Optional.empty() : attunementIngredient;
        downgradeTo = downgradeTo == null ? Optional.empty() : downgradeTo;
        sourcePack = sourcePack == null ? Optional.empty() : sourcePack;
        experienceRequirementOverride = experienceRequirementOverride == null ? Optional.empty() : experienceRequirementOverride;
        upgradeIngredientOverride = upgradeIngredientOverride == null ? Optional.empty() : upgradeIngredientOverride;
        displayBranchId = displayBranchId == null ? Optional.empty() : displayBranchId;
        compatAmplifiers = compatAmplifiers == null ? Map.of() : Map.copyOf(compatAmplifiers);
    }

    public int effectiveExperienceRequirement(final int tierDefault) {
        return this.experienceRequirementOverride.orElse(tierDefault);
    }
}
