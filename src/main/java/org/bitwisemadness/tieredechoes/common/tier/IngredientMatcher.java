package org.bitwisemadness.tieredechoes.common.tier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

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
        return switch (this.kind) {
            case ITEM -> Objects.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()), this.id);
            case TAG -> stack.is(ItemTags.create(this.id));
        };
    }

    public String displayKey() {
        return this.kind == Kind.ITEM ? this.id.toString() : "#" + this.id;
    }
}
