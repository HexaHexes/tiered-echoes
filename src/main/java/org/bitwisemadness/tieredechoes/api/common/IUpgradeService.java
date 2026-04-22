package org.bitwisemadness.tieredechoes.api.common;

import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;

public interface IUpgradeService {
    ItemStack applyPreviewTier(ItemStack stack, TierDefinition tier);

    ItemStack applyTier(ItemStack stack, TierDefinition tier);

    ItemStack downgrade(ItemStack stack);
}
