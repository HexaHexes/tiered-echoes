package org.bitwisemadness.tieredechoes.common.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.bitwisemadness.tieredechoes.common.registry.TERecipes;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;

/**
 * Moves an item down one tier on a smithing table.
 *
 * <p>Stored experience is kept, because the downgrade undoes a <em>tier</em> decision rather than the
 * time spent using the item. Branch selection is cleared for the same reason an upgrade clears it:
 * the pending choice described a transition that is no longer pending.</p>
 */
public final class TierDowngradeRecipe extends TierTransitionRecipe {

    public TierDowngradeRecipe(final ResourceLocation recipeId,
                               final Ingredient template,
                               final Ingredient base,
                               final Ingredient addition,
                               final TierDomain domain,
                               final ResourceLocation sourceTier,
                               final ResourceLocation targetTier) {
        super(recipeId, template, base, addition, domain, sourceTier, targetTier);
    }

    @Override
    protected ItemStack applyTransition(final ItemStack input, final TierDefinition targetTier) {
        return UpgradeService.getInstance().applyTier(input, targetTier);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TERecipes.TIER_DOWNGRADE.get();
    }
}
