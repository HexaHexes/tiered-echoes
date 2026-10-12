package org.bitwisemadness.tieredechoes.common.registry;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.recipe.TierBootstrapRecipe;
import org.bitwisemadness.tieredechoes.common.recipe.TierDowngradeRecipe;
import org.bitwisemadness.tieredechoes.common.recipe.TierRecipeSerializer;
import org.bitwisemadness.tieredechoes.common.recipe.TierUpgradeRecipe;

/**
 * Recipe serializers for the smithing transitions.
 *
 * <p>The bootstrap recipe gets its own serializer rather than reusing the upgrade one, because
 * recipes are re-created from the packet on the client: sharing a serializer would rebuild every
 * bootstrap recipe as a plain upgrade recipe, whose source-tier test can never be satisfied, and the
 * smithing menu would show an empty result slot for a transition the server is about to perform.</p>
 *
 * <p>All three report themselves as {@code RecipeType.SMITHING} through {@code SmithingRecipe}'s
 * default, so the vanilla smithing table menu picks them up without a menu mixin.</p>
 */
public final class TERecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.RECIPE_SERIALIZERS, TieredEchoes.MOD_ID);

    public static final RegistryObject<RecipeSerializer<TierUpgradeRecipe>> TIER_UPGRADE =
            SERIALIZERS.register("tier_upgrade", () -> new TierRecipeSerializer<>(TierUpgradeRecipe::new));

    public static final RegistryObject<RecipeSerializer<TierBootstrapRecipe>> TIER_BOOTSTRAP =
            SERIALIZERS.register("tier_bootstrap", () -> new TierRecipeSerializer<>(TierBootstrapRecipe::new));

    public static final RegistryObject<RecipeSerializer<TierDowngradeRecipe>> TIER_DOWNGRADE =
            SERIALIZERS.register("tier_downgrade", () -> new TierRecipeSerializer<>(TierDowngradeRecipe::new));

    private TERecipes() {
    }
}
