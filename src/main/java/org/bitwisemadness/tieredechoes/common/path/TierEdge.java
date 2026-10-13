package org.bitwisemadness.tieredechoes.common.path;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.util.Map;
import java.util.Optional;

/**
 * One hop between two tiers of one domain.
 *
 * <p>A tier declares what it costs to join and what it costs to leave; this is the record of a
 * single such claim. It is deliberately not the recipe itself — {@code TierRecipeGenerator} turns
 * edges into recipes at reload time so that a datapack can change the graph without the game
 * holding on to stale ones.</p>
 *
 * @param from             the tier this edge leaves
 * @param to               the tier this edge reaches
 * @param domain           the ladder the edge belongs to
 * @param registrationOrder the position this edge held among its siblings in the file that
 *                          declared it, so a repeated reload reproduces the same order
 * @param additionIngredient the smithing addition slot for the upgrade, if the edge names one
 * @param downgradeTo      the tier a downgrade from here lands on, if one was declared
 * @param sourcePack       the pack that declared the edge, for validation messages
 * @param displayBranchId  a label for a fork, shown to the player when several edges leave the
 *                          same tier
 * @param compatAmplifiers per-mod scaling applied to this edge's effect
 */
public record TierEdge(
        ResourceLocation from,
        ResourceLocation to,
        TierDomain domain,
        int registrationOrder,
        Optional<IngredientMatcher> additionIngredient,
        Optional<ResourceLocation> downgradeTo,
        Optional<String> sourcePack,
        Optional<String> displayBranchId,
        Map<String, Double> compatAmplifiers
) {
    public TierEdge {
        if (domain == null) {
            throw new IllegalArgumentException("TierEdge requires a domain: " + from + " -> " + to);
        }
        additionIngredient = additionIngredient == null ? Optional.empty() : additionIngredient;
        downgradeTo = downgradeTo == null ? Optional.empty() : downgradeTo;
        sourcePack = sourcePack == null ? Optional.empty() : sourcePack;
        displayBranchId = displayBranchId == null ? Optional.empty() : displayBranchId;
        compatAmplifiers = compatAmplifiers == null ? Map.of() : Map.copyOf(compatAmplifiers);
    }
}
