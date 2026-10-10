package org.bitwisemadness.tieredechoes.common.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;

/**
 * A smithing-table transition between two tiers of one domain.
 *
 * <p>{@link net.minecraft.world.item.crafting.SmithingRecipe} keeps the vanilla {@code SmithingMenu}
 * usable as-is: the menu collects every {@code RecipeType.SMITHING} recipe and asks each one whether
 * the three slots match. The extra constraint this class adds is the <em>source tier</em> — an item
 * already sitting on {@code rare} must not be able to re-run the {@code common -> uncommon} recipe,
 * which is what makes branching transitions mutually exclusive in the menu.</p>
 *
 * <p>An item carrying no Tiered Echoes NBT at all is treated as sitting on the initial tier of its
 * domain, so a freshly crafted vanilla pickaxe can be tiered up for the first time.</p>
 */
public abstract class TierTransitionRecipe implements SmithingRecipe {
    private final ResourceLocation id;
    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final TierDomain domain;
    private final ResourceLocation sourceTier;
    private final ResourceLocation targetTier;

    protected TierTransitionRecipe(final ResourceLocation id,
                                   final Ingredient template,
                                   final Ingredient base,
                                   final Ingredient addition,
                                   final TierDomain domain,
                                   final ResourceLocation sourceTier,
                                   final ResourceLocation targetTier) {
        this.id = id;
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.domain = domain;
        this.sourceTier = sourceTier;
        this.targetTier = targetTier;
    }

    /**
     * Produces the resulting stack. Implementations are expected to keep stored experience — a
     * material upgrade must never erase the progress the item already earned.
     */
    protected abstract ItemStack applyTransition(ItemStack input, TierDefinition targetTier);

    @Override
    public boolean matches(final Container container, final Level level) {
        if (!this.template.test(container.getItem(SmithingMenu.TEMPLATE_SLOT))
                || !this.base.test(container.getItem(SmithingMenu.BASE_SLOT))
                || !this.addition.test(container.getItem(SmithingMenu.ADDITIONAL_SLOT))) {
            return false;
        }
        final TierDefinition current = TierService.getInstance()
                .resolveCurrentTier(container.getItem(SmithingMenu.BASE_SLOT), this.domain)
                .orElse(null);
        return current != null
                && current.domain() == this.domain
                && current.id().equals(this.sourceTier);
    }

    @Override
    public ItemStack assemble(final Container container, final RegistryAccess registryAccess) {
        final ItemStack input = container.getItem(SmithingMenu.BASE_SLOT);
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final TierDefinition target = TierService.getInstance().getTier(this.targetTier, this.domain).orElse(null);
        if (target == null) {
            return ItemStack.EMPTY;
        }
        return this.applyTransition(input.copy(), target);
    }

    /**
     * The smithing menu resolves its result slot through {@link #assemble}, never through this method,
     * and a transition recipe has no single representative item — the result is whatever the player
     * put in the base slot. Returning empty keeps that fact honest instead of inventing a preview.
     */
    @Override
    public ItemStack getResultItem(final RegistryAccess registryAccess) {
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        final NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(this.template);
        ingredients.add(this.base);
        ingredients.add(this.addition);
        return ingredients;
    }

    @Override
    public boolean isIncomplete() {
        return net.minecraftforge.common.ForgeHooks.hasNoElements(this.template)
                || net.minecraftforge.common.ForgeHooks.hasNoElements(this.base)
                || net.minecraftforge.common.ForgeHooks.hasNoElements(this.addition);
    }

    @Override
    public boolean isTemplateIngredient(final ItemStack stack) {
        return this.template.test(stack);
    }

    @Override
    public boolean isBaseIngredient(final ItemStack stack) {
        return this.base.test(stack);
    }

    @Override
    public boolean isAdditionIngredient(final ItemStack stack) {
        return this.addition.test(stack);
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    public TierDomain domain() {
        return this.domain;
    }

    public ResourceLocation sourceTier() {
        return this.sourceTier;
    }

    public ResourceLocation targetTier() {
        return this.targetTier;
    }

    public Ingredient templateIngredient() {
        return this.template;
    }

    public Ingredient baseIngredient() {
        return this.base;
    }

    public Ingredient additionIngredient() {
        return this.addition;
    }
}
