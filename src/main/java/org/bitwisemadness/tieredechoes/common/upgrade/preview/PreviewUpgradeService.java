package org.bitwisemadness.tieredechoes.common.upgrade.preview;

import net.minecraft.network.chat.Component;
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
        final ItemStack preview = UpgradeService.getInstance().applyPreviewTier(stack, tier);
        preview.setHoverName(Component.literal(preview.getHoverName().getString() + " [" + tier.id().getPath() + "]"));
        return preview;
    }
}
