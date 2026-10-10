package org.bitwisemadness.tieredechoes.common.recipe;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

/**
 * Shared serializer for {@link TierTransitionRecipe} subclasses.
 *
 * <p>The JSON schema is deliberately the same shape as {@code minecraft:smithing_transform} —
 * {@code template}, {@code base}, {@code addition} — with {@code domain}, {@code source_tier} and
 * {@code target_tier} added on top, so hand-written datapack recipes read like vanilla ones.</p>
 */
public final class TierRecipeSerializer<T extends TierTransitionRecipe> implements RecipeSerializer<T> {

    @FunctionalInterface
    public interface Factory<T extends TierTransitionRecipe> {
        T create(ResourceLocation recipeId, Ingredient template, Ingredient base, Ingredient addition,
                 TierDomain domain, ResourceLocation sourceTier, ResourceLocation targetTier);
    }

    private final Factory<T> factory;

    public TierRecipeSerializer(final Factory<T> factory) {
        this.factory = factory;
    }

    @Override
    public T fromJson(final ResourceLocation recipeId, final JsonObject json) {
        final String domainName = GsonHelper.getAsString(json, "domain");
        final TierDomain domain = TierDomain.byName(domainName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tier domain \"" + domainName + "\""));
        return this.factory.create(
                recipeId,
                Ingredient.fromJson(GsonHelper.getNonNull(json, "template")),
                Ingredient.fromJson(GsonHelper.getNonNull(json, "base")),
                Ingredient.fromJson(GsonHelper.getNonNull(json, "addition")),
                domain,
                new ResourceLocation(GsonHelper.getAsString(json, "source_tier")),
                new ResourceLocation(GsonHelper.getAsString(json, "target_tier")));
    }

    @Override
    public T fromNetwork(final ResourceLocation recipeId, final FriendlyByteBuf buf) {
        final Ingredient template = Ingredient.fromNetwork(buf);
        final Ingredient base = Ingredient.fromNetwork(buf);
        final Ingredient addition = Ingredient.fromNetwork(buf);
        final String domainName = buf.readUtf();
        final TierDomain domain = TierDomain.byName(domainName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tier domain \"" + domainName + "\""));
        final ResourceLocation sourceTier = buf.readResourceLocation();
        final ResourceLocation targetTier = buf.readResourceLocation();
        return this.factory.create(recipeId, template, base, addition, domain, sourceTier, targetTier);
    }

    @Override
    public void toNetwork(final FriendlyByteBuf buf, final T recipe) {
        recipe.templateIngredient().toNetwork(buf);
        recipe.baseIngredient().toNetwork(buf);
        recipe.additionIngredient().toNetwork(buf);
        buf.writeUtf(recipe.domain().getSerializedName());
        buf.writeResourceLocation(recipe.sourceTier());
        buf.writeResourceLocation(recipe.targetTier());
    }
}
