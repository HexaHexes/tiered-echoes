package org.bitwisemadness.tieredechoes.client.creative;

import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

public record CreativePreviewEntry(Item item, TierDefinition tier, ItemStack previewStack, Set<TierDomain> domains) {
}
