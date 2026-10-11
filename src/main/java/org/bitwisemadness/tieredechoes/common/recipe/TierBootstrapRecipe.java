package org.bitwisemadness.tieredechoes.common.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.registry.TERecipes;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;

/**
 * The step onto the ladder: an untiered item becomes the domain's first rung.
 *
 * <p>This is the one transition whose source is the <em>absence</em> of a tier rather than a tier,
 * so it cannot share {@link TierTransitionRecipe}'s matching rule. Every other recipe asks "is the
 * base sitting on rung X"; this one asks "is the base sitting on no rung at all", which is what stops
 * a rare sword from re-running it and quietly dropping back to common.</p>
 *
 * <p>It extends {@link TierUpgradeRecipe} so that assembly, ingredient exposure and experience
 * handling are inherited rather than repeated — the only behaviour that differs is the test. The
 * {@link #UNTIERED} marker exists purely so the recipe can cross the network: a recipe carries its
 * source tier in the packet, and a null there would throw. No tier definition claims that id, so it
 * can never be mistaken for a real rung, and a datapack that spells it out in JSON is overridden
 * rather than honoured.</p>
 */
public final class TierBootstrapRecipe extends TierUpgradeRecipe {

    /** A source tier that names no rung, used only as the value written to the network buffer. */
    public static final ResourceLocation UNTIERED = new ResourceLocation(TieredEchoes.MOD_ID, "untiered");

    public TierBootstrapRecipe(final ResourceLocation recipeId,
                               final Ingredient template,
                               final Ingredient base,
                               final Ingredient addition,
                               final TierDomain domain,
                               final ResourceLocation ignoredSourceTier,
                               final ResourceLocation targetTier) {
        super(recipeId, template, base, addition, domain, UNTIERED, targetTier);
    }

    @Override
    public boolean matches(final Container container, final Level level) {
        if (!this.templateIngredient().test(container.getItem(SmithingMenu.TEMPLATE_SLOT))
                || !this.baseIngredient().test(container.getItem(SmithingMenu.BASE_SLOT))
                || !this.additionIngredient().test(container.getItem(SmithingMenu.ADDITIONAL_SLOT))) {
            return false;
        }
        // Inverted relative to the base class. The slot ingredients already guarantee the item
        // belongs to this domain, so an empty tier here means it has not joined the ladder yet.
        return TierService.getInstance()
                .resolveCurrentTier(container.getItem(SmithingMenu.BASE_SLOT), this.domain())
                .isEmpty();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TERecipes.TIER_BOOTSTRAP.get();
    }
}
