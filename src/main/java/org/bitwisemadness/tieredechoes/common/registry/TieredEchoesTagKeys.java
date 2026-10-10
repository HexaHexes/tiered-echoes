package org.bitwisemadness.tieredechoes.common.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.bitwisemadness.tieredechoes.TieredEchoes;

/**
 * Item tags used as smithing base-slot constraints, one per {@code TierDomain}.
 *
 * <p>They exist so a datapack can make modded gear tierable by adding items to the tag rather than
 * by writing a new recipe — the recipe generator derives its {@code base} ingredient from here.</p>
 */
public final class TieredEchoesTagKeys {

    private TieredEchoesTagKeys() {
    }

    public static TagKey<Item> item(final String path) {
        return ItemTags.create(new ResourceLocation(TieredEchoes.MOD_ID, path));
    }
}
