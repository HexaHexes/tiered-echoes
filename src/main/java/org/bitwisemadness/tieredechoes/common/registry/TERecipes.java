package org.bitwisemadness.tieredechoes.common.registry;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.recipe.TierDowngradeRecipe;
import org.bitwisemadness.tieredechoes.common.recipe.TierRecipeSerializer;
import org.bitwisemadness.tieredechoes.common.recipe.TierUpgradeRecipe;

/**
 * Recipe serializers for the two smithing transitions.
 *
 * <p>Both report themselves as {@code RecipeType.SMITHING} through {@code SmithingRecipe}'s default,
 * so the vanilla smithing table menu picks them up without a menu mixin.</p>
 */
public final class TERecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.RECIPE_SERIALIZERS, TieredEchoes.MOD_ID);

    public static final RegistryObject<RecipeSerializer<TierUpgradeRecipe>> TIER_UPGRADE =
            SERIALIZERS.register("tier_upgrade", () -> new TierRecipeSerializer<>(TierUpgradeRecipe::new));

    public static final RegistryObject<RecipeSerializer<TierDowngradeRecipe>> TIER_DOWNGRADE =
            SERIALIZERS.register("tier_downgrade", () -> new TierRecipeSerializer<>(TierDowngradeRecipe::new));

    private TERecipes() {
    }
}
