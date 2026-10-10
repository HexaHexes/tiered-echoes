package org.bitwisemadness.tieredechoes.common.upgrade.preview;

import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;

public final class PreviewUpgradeService {
    private static final PreviewUpgradeService INSTANCE = new PreviewUpgradeService();

    private PreviewUpgradeService() {
    }

    public static PreviewUpgradeService getInstance() {
        return INSTANCE;
    }

    public ItemStack createPreviewStack(final ItemStack stack, final TierDefinition tier) {
        return UpgradeService.getInstance().applyPreviewTier(stack, tier);
    }
}
