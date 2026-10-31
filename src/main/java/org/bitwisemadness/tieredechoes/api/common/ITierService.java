package org.bitwisemadness.tieredechoes.api.common;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

public interface ITierService {
    List<Item> collectPreviewItems(TierDomain domain);

    List<TierDefinition> getOrderedValidTiersForItem(Item item, TierDomain domain);

    Optional<TierDefinition> getTier(ResourceLocation tierId);
}
