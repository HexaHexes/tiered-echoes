package org.bitwisemadness.tieredechoes.common.tier;

import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

public record IngredientMatcher(Kind kind, ResourceLocation id) {
    public enum Kind {
        ITEM,
        TAG
    }

    public IngredientMatcher {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    public boolean matches(final ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return this.toIngredient().test(stack);
    }

    /**
     * The vanilla {@link Ingredient} equivalent. Building this lazily — rather than caching — means
     * the result picks up datapack tag changes on the next reload instead of holding a stale bake.
     */
    public Ingredient toIngredient() {
        return switch (this.kind) {
            case ITEM -> {
                final Item item = BuiltInRegistries.ITEM.getOptional(this.id).orElse(null);
                yield item == null ? Ingredient.EMPTY : Ingredient.of(item);
            }
            case TAG -> Ingredient.of(ItemTags.create(this.id));
        };
    }

    public String displayKey() {
        return this.kind == Kind.ITEM ? this.id.toString() : "#" + this.id;
    }
}
