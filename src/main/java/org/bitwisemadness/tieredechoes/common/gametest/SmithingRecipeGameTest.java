package org.bitwisemadness.tieredechoes.common.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.bitwisemadness.tieredechoes.common.data.loader.TierRecipeGenerator;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationReport;
import org.bitwisemadness.tieredechoes.common.path.PathService;
import org.bitwisemadness.tieredechoes.common.path.TierEdge;
import org.bitwisemadness.tieredechoes.common.policy.PolicyService;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;
import org.bitwisemadness.tieredechoes.common.recipe.TierBootstrapRecipe;
import org.bitwisemadness.tieredechoes.common.recipe.TierTransitionRecipe;
import org.bitwisemadness.tieredechoes.common.recipe.TierUpgradeRecipe;
import org.bitwisemadness.tieredechoes.common.template.TemplateDefinition;
import org.bitwisemadness.tieredechoes.common.template.TemplateService;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;

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
    private static final ResourceLocation BOOTSTRAP =
            new ResourceLocation(TieredEchoes.MOD_ID, "tier_upgrade/weapon/untiered_to_common");
    private static final ResourceLocation DOWNGRADE =
            new ResourceLocation(TieredEchoes.MOD_ID, "tier_downgrade/weapon/uncommon_to_common");

    private SmithingRecipeGameTest() {
    }

    @GameTest
    public static void theUpgradeAndBootstrapRecipesAreRegisteredUnderTheirStableIds(final GameTestHelper helper) {
        helper.assertTrue(recipe(helper, UPGRADE) instanceof TierUpgradeRecipe,
                UPGRADE + " should be a tier upgrade recipe");
        helper.assertTrue(recipe(helper, BOOTSTRAP) instanceof TierBootstrapRecipe,
                BOOTSTRAP + " should be a tier bootstrap recipe");
        helper.succeed();
    }

    /**
     * The shipped ladder declares no way down, so nothing may be generated for one — and the switch
     * that would publish it is off on top of that. Their data path stays either way.
     */
    @GameTest
    public static void noDowngradeRecipeIsGenerated(final GameTestHelper helper) {
        helper.assertFalse(helper.getLevel().getRecipeManager().byKey(DOWNGRADE).isPresent(),
                DOWNGRADE + " was generated, but the shipped ladder declares no way down");
        helper.succeed();
    }

    /**
     * The part the shipped ladder cannot cover: a route a pack <em>does</em> declare, and the switch
     * that decides whether it is published.
     *
     * <p>Both halves matter. A declared route with the switch off must stay unpublished, or the
     * operator's override would be a lie; a declared route with the switch on must actually reach
     * the table, or the declaration would be decoration. The ladder is given a route here and the
     * switch is turned off around it, because neither condition can be observed from a ladder that
     * declares nothing.</p>
     */
    @GameTest
    public static void aDeclaredWayDownIsGeneratedOnlyWhileTheSwitchAllowsIt(final GameTestHelper helper) {
        final PathService paths = PathService.getInstance();
        final TemplateService templates = TemplateService.getInstance();
        final PolicyService policy = PolicyService.getInstance();
        final List<TierEdge> edgesBefore = paths.getAllEdgesLike();
        final List<TemplateDefinition> templatesBefore = templates.declaredTemplates();
        final TierPolicy policyBefore = policy.get();
        try {
            paths.replaceAll(withADowngradeRoute(edgesBefore));
            // The route's template is the one named by the rung being left behind, which is not an
            // item the mod registers: a pack that adds the way down declares what to spend on it.
            templates.replaceAll(List.of(new TemplateDefinition(
                    new ResourceLocation(TieredEchoes.MOD_ID, "uncommon_downgrade_template"),
                    Optional.of(new ResourceLocation("minecraft", "amethyst_shard")),
                    Optional.empty(),
                    Optional.empty())));
            policy.replaceFromDatapack(new TierPolicy(true, true, true, true, true));

            TierRecipeGenerator.regenerateAndInject(helper.getLevel().getRecipeManager(), new ValidationReport());
            final TierTransitionRecipe down = recipe(helper, DOWNGRADE);
            final Container slots = new SimpleContainer(slotItem(helper, down.templateIngredient(), "template"),
                    GameTestTiers.tieredSword(helper, "uncommon"),
                    slotItem(helper, down.additionIngredient(), "addition"));
            helper.assertTrue(down.matches(slots, helper.getLevel()),
                    "an uncommon weapon with the uncommon downgrade template and the edge's addition should match");
            helper.assertTrue(currentTierId(down.assemble(slots, helper.getLevel().registryAccess()))
                            .filter(GameTestTiers.tierId("common")::equals).isPresent(),
                    "the way down must land on the rung it declares");

            policy.replaceFromDatapack(TierPolicy.defaults());
            TierRecipeGenerator.regenerateAndInject(helper.getLevel().getRecipeManager(), new ValidationReport());
            helper.assertFalse(helper.getLevel().getRecipeManager().byKey(DOWNGRADE).isPresent(),
                    "the switch is off, so the declared route must not be published");
        } finally {
            paths.replaceAll(edgesBefore);
            templates.replaceAll(templatesBefore);
            policy.replaceFromDatapack(policyBefore);
            TierRecipeGenerator.regenerateAndInject(helper.getLevel().getRecipeManager(), new ValidationReport());
        }
        helper.succeed();
    }

    /** The weapon ladder's opening step, re-declared with somewhere for the way down to land. */
    private static List<TierEdge> withADowngradeRoute(final List<TierEdge> edges) {
        final List<TierEdge> replaced = new ArrayList<>(edges);
        replaced.removeIf(edge -> edge.domain() == TierDomain.WEAPON
                && edge.from().equals(GameTestTiers.tierId("common"))
                && edge.to().equals(GameTestTiers.tierId("uncommon")));
        replaced.add(new TierEdge(
                GameTestTiers.tierId("common"),
                GameTestTiers.tierId("uncommon"),
                TierDomain.WEAPON,
                0,
                Optional.of(new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", "iron_ingot"))),
                Optional.of(GameTestTiers.tierId("common")),
                Optional.of(TieredEchoes.MOD_ID),
                Optional.of("weapon_common_to_uncommon"),
                Map.of()));
        return replaced;
    }

    /**
     * The headline promise: a smithing upgrade changes the tier and leaves everything else the item
     * carries exactly where it was.
     */
    @GameTest
    public static void aSmithingUpgradeCarriesOverEnchantmentsAndDurability(final GameTestHelper helper) {
        final TierTransitionRecipe upgrade = recipe(helper, UPGRADE);
        final ItemStack base = GameTestTiers.tieredSword(helper, "common");
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
        helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, result) == 3,
                "enchantments must survive a material upgrade");
        helper.assertTrue(result.getDamageValue() == 11, "durability must survive a material upgrade");
        helper.assertTrue(currentTierId(base).filter(GameTestTiers.tierId("common")::equals).isPresent(),
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

    /**
     * The step onto the ladder. An untiered sword has no rung recorded, so the ordinary upgrade
     * recipe's source-tier test can never be satisfied for it — the bootstrap recipe's inverted test
     * is what puts the common template and copper to work. It must then hand over to that ordinary
     * recipe rather than stay applicable forever.
     */
    @GameTest
    public static void anUntieredSwordJoinsTheLadderThroughSmithing(final GameTestHelper helper) {
        final TierTransitionRecipe bootstrap = recipe(helper, BOOTSTRAP);
        final ItemStack base = new ItemStack(Items.IRON_SWORD);
        base.enchant(Enchantments.SHARPNESS, 3);
        helper.assertTrue(bootstrap.isBaseIngredient(base), "an iron sword should satisfy the weapon base tag");

        final Container slots = new SimpleContainer(slotItem(helper, bootstrap.templateIngredient(), "template"),
                base, slotItem(helper, bootstrap.additionIngredient(), "addition"));
        helper.assertTrue(bootstrap.matches(slots, helper.getLevel()),
                "an untiered weapon with the common template and the ladder's opening ingredient should match");

        final ItemStack result = bootstrap.assemble(slots, helper.getLevel().registryAccess());

        helper.assertTrue(currentTierId(result).filter(GameTestTiers.tierId("common")::equals).isPresent(),
                "expected the item to land on common but it is on " + currentTierId(result));
        helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, result) == 3,
                "enchantments must survive joining the ladder");

        // The same bootstrap slots with the now-tiered item in them: only the recorded rung stands
        // between this and a second, free trip up the ladder.
        final Container repeatedSlots = new SimpleContainer(slotItem(helper, bootstrap.templateIngredient(), "template"),
                result, slotItem(helper, bootstrap.additionIngredient(), "addition"));
        helper.assertFalse(bootstrap.matches(repeatedSlots, helper.getLevel()),
                "an item that already has a rung must not be able to join the ladder a second time");

        // Both slots belong to the recipe being asked, not to the one before it: the bootstrap spends
        // the common template and copper, the ordinary upgrade spends uncommon's.
        final TierTransitionRecipe upgrade = recipe(helper, UPGRADE);
        final Container upgradeSlots = new SimpleContainer(slotItem(helper, upgrade.templateIngredient(), "template"),
                result, slotItem(helper, upgrade.additionIngredient(), "addition"));
        helper.assertTrue(upgrade.matches(upgradeSlots, helper.getLevel()),
                "the ordinary upgrade recipe must take over from where the bootstrap recipe stops");
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
