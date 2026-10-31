package org.bitwisemadness.tieredechoes.common.data.loader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.junit.jupiter.api.Test;

/**
 * The generated recipe ids are the mod's own namespace, and the injector strips them before
 * re-adding. Mis-classifying either way is a real bug: too narrow and {@code /reload} accumulates
 * duplicates until {@code replaceRecipes} throws; too wide and a datapack's hand-written recipe
 * silently disappears.
 */
class TierRecipeGeneratorTest {

    @Test
    void claimsBothGeneratedPrefixes() {
        assertTrue(TierRecipeGenerator.isGeneratedRecipeId(
                new ResourceLocation(TieredEchoes.MOD_ID, "tier_upgrade/weapon/common_to_uncommon")));
        assertTrue(TierRecipeGenerator.isGeneratedRecipeId(
                new ResourceLocation(TieredEchoes.MOD_ID, "tier_downgrade/armor/rare_to_uncommon")));
    }

    @Test
    void leavesDatapackRecipesInTheModNamespaceAlone() {
        assertFalse(TierRecipeGenerator.isGeneratedRecipeId(
                new ResourceLocation(TieredEchoes.MOD_ID, "recipes/my_custom_smithing")));
        assertFalse(TierRecipeGenerator.isGeneratedRecipeId(
                new ResourceLocation(TieredEchoes.MOD_ID, "tier_upgrades/typo")));
    }

    @Test
    void leavesOtherNamespacesAloneEvenWhenTheyCopyThePath() {
        assertFalse(TierRecipeGenerator.isGeneratedRecipeId(
                new ResourceLocation("othermod", "tier_upgrade/weapon/common_to_uncommon")));
    }
}
