package org.bitwisemadness.tieredechoes.common.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.recipe.TierDowngradeRecipe;
import org.bitwisemadness.tieredechoes.common.recipe.TierTransitionRecipe;
import org.bitwisemadness.tieredechoes.common.recipe.TierUpgradeRecipe;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;

import java.util.List;
import java.util.Optional;

/**
 * The smithing contract, exercised against a live {@code RecipeManager}.
 *
 * <p>Unit tests can check the recipe objects in isolation but not the parts that need a server:
 * tag resolution for the base ingredient, the reload-time injection of the generated ids, or the
 * vanilla menu's own recipe lookup.</p>
 */
@GameTestHolder(TieredEchoes.MOD_ID)
public final class SmithingRecipeGameTest {

    private static final ResourceLocation UPGRADE =
            new ResourceLocation(TieredEchoes.MOD_ID, "tier_upgrade/weapon/common_to_uncommon");
    private static final ResourceLocation DOWNGRADE =
            new ResourceLocation(TieredEchoes.MOD_ID, "tier_downgrade/weapon/uncommon_to_common");

    private SmithingRecipeGameTest() {
    }

    @GameTest
    public static void bothGeneratedTransitionsAreRegisteredUnderTheirStableIds(final GameTestHelper helper) {
        helper.assertTrue(recipe(helper, UPGRADE) instanceof TierUpgradeRecipe,
                UPGRADE + " should be a tier upgrade recipe");
        helper.assertTrue(recipe(helper, DOWNGRADE) instanceof TierDowngradeRecipe,
                DOWNGRADE + " should be a tier downgrade recipe");
        helper.succeed();
    }

    /**
     * The headline promise: a smithing upgrade changes the tier and leaves everything else the item
     * carries exactly where it was.
     */
    @GameTest
    public static void aSmithingUpgradeCarriesOverExperienceAndEnchantments(final GameTestHelper helper) {
        final TierTransitionRecipe upgrade = recipe(helper, UPGRADE);
        final ItemStack base = GameTestTiers.tieredSword(helper, "common");
        UpgradeService.getInstance().setStoredExperience(base, 55);
        base.enchant(Enchantments.SHARPNESS, 3);
        base.setDamageValue(11);
        helper.assertTrue(upgrade.isBaseIngredient(base),
                "an iron sword should satisfy the weapon base tag");

        final Container slots = new SimpleContainer(slotItem(helper, upgrade.templateIngredient(), "template"),
                base, slotItem(helper, upgrade.additionIngredient(), "addition"));
        helper.assertTrue(upgrade.matches(slots, helper.getLevel()),
                "a common weapon with the uncommon template and the edge's addition should match");

        final ItemStack result = upgrade.assemble(slots, helper.getLevel().registryAccess());

        helper.assertTrue(result.getItem() == Items.IRON_SWORD, "the material must not change");
        helper.assertTrue(currentTierId(result).filter(GameTestTiers.tierId("uncommon")::equals).isPresent(),
                "expected the item to land on uncommon but it is on " + currentTierId(result));
        helper.assertTrue(UpgradeService.getInstance().getStoredExperience(result) == 55,
                "stored experience must survive a material upgrade");
        helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, result) == 3,
                "enchantments must survive a material upgrade");
        helper.assertTrue(result.getDamageValue() == 11, "durability must survive a material upgrade");
        helper.assertTrue(UpgradeService.getInstance().getStoredExperience(base) == 55
                        && currentTierId(base).filter(GameTestTiers.tierId("common")::equals).isPresent(),
                "the stack sitting in the slot must be left untouched");
        helper.succeed();
    }

    /**
     * Without the source-tier check, an item that had already climbed could run the same transition
     * again and every branch below it would be reachable from the menu at once.
     */
    @GameTest
    public static void anItemPastTheSourceTierCannotRepeatTheRecipe(final GameTestHelper helper) {
        final TierTransitionRecipe upgrade = recipe(helper, UPGRADE);
        final Container slots = new SimpleContainer(slotItem(helper, upgrade.templateIngredient(), "template"),
                GameTestTiers.tieredSword(helper, "uncommon"),
                slotItem(helper, upgrade.additionIngredient(), "addition"));

        helper.assertFalse(upgrade.matches(slots, helper.getLevel()),
                "an item already on uncommon must not be able to run common -> uncommon again");
        helper.succeed();
    }

    /** The menu never asks recipes directly — it asks the manager for everything matching the type. */
    @GameTest
    public static void theVanillaMenuSurfacesTheGeneratedRecipe(final GameTestHelper helper) {
        final TierTransitionRecipe upgrade = recipe(helper, UPGRADE);
        final Container slots = new SimpleContainer(slotItem(helper, upgrade.templateIngredient(), "template"),
                GameTestTiers.tieredSword(helper, "common"),
                slotItem(helper, upgrade.additionIngredient(), "addition"));

        final List<SmithingRecipe> visible = helper.getLevel().getRecipeManager()
                .getRecipesFor(RecipeType.SMITHING, slots, helper.getLevel());

        helper.assertTrue(visible.stream().anyMatch(candidate -> candidate.getId().equals(UPGRADE)),
                "the smithing menu should surface " + UPGRADE + " but only saw " + visible.size() + " recipes");
        helper.succeed();
    }

    @GameTest
    public static void aDowngradeStepsBackWithoutPunishingProgress(final GameTestHelper helper) {
        final TierTransitionRecipe downgrade = recipe(helper, DOWNGRADE);
        final ItemStack base = GameTestTiers.tieredSword(helper, "uncommon");
        UpgradeService.getInstance().setStoredExperience(base, 55);
        base.enchant(Enchantments.SHARPNESS, 3);
        helper.assertTrue(downgrade.isBaseIngredient(base), "an iron sword should satisfy the weapon base tag");

        final Container slots = new SimpleContainer(slotItem(helper, downgrade.templateIngredient(), "template"),
                base, slotItem(helper, downgrade.additionIngredient(), "addition"));
        helper.assertTrue(downgrade.matches(slots, helper.getLevel()),
                "an uncommon weapon with the uncommon downgrade template should match");

        final ItemStack result = downgrade.assemble(slots, helper.getLevel().registryAccess());

        helper.assertTrue(currentTierId(result).filter(GameTestTiers.tierId("common")::equals).isPresent(),
                "expected the item to step back to common but it is on " + currentTierId(result));
        helper.assertTrue(UpgradeService.getInstance().getStoredExperience(result) == 55,
                "a downgrade is a route back down, not a punishment");
        helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, result) == 3,
                "enchantments must survive a downgrade too");
        helper.succeed();
    }

    /** Returns the transition itself, not just {@link SmithingRecipe}, to reach the slot accessors. */
    private static TierTransitionRecipe recipe(final GameTestHelper helper, final ResourceLocation id) {
        final Optional<? extends Recipe<?>> found = helper.getLevel().getRecipeManager().byKey(id);
        if (found.isPresent() && found.get() instanceof TierTransitionRecipe transition) {
            return transition;
        }
        helper.fail("Expected a smithing recipe at " + id + "; check the tieredechoes validation line");
        throw new AssertionError("GameTestHelper#fail always throws");
    }

    /**
     * Pulls a single stack out of an ingredient. Doing this through the recipe rather than naming a
     * concrete item keeps the test honest if a datapack swaps the template out.
     */
    private static ItemStack slotItem(final GameTestHelper helper, final Ingredient ingredient, final String slot) {
        final ItemStack[] items = ingredient.getItems();
        helper.assertTrue(items.length > 0 && !items[0].isEmpty(),
                "the " + slot + " ingredient resolved to nothing");
        return items[0].copy();
    }

    private static Optional<ResourceLocation> currentTierId(final ItemStack stack) {
        return UpgradeService.getInstance().getCurrentTier(stack).map(TierDefinition::id);
    }
}
