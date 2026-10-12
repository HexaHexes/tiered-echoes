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
 * Moves an item up one tier on a smithing table, preserving the experience it has already stored.
 *
 * <p>Because the transition is applied on top of a copy of the base stack rather than onto a
 * pre-built result item, everything else the item carries — enchantments, durability, custom name,
 * the {@code TieredEchoes} tag itself — survives a material upgrade unchanged.</p>
 *
 * <p>Not {@code final}: {@link TierBootstrapRecipe} extends it to inherit assembly and ingredient
 * handling, overriding only the test that decides when the recipe applies.</p>
 */
public class TierUpgradeRecipe extends TierTransitionRecipe {

    public TierUpgradeRecipe(final ResourceLocation recipeId,
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
        return TERecipes.TIER_UPGRADE.get();
    }
}
