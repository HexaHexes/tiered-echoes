package org.bitwisemadness.tieredechoes.client.creative;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.util.Set;

public record CreativePreviewEntry(Item item, TierDefinition tier, ItemStack previewStack, Set<TierDomain> domains) {
}
