package org.bitwisemadness.tieredechoes.common.compat.apotheosis;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

public final class ApotheosisCompatBridge {
    private static final ApotheosisCompatBridge INSTANCE = new ApotheosisCompatBridge();

    private ApotheosisCompatBridge() {
    }

    public static ApotheosisCompatBridge getInstance() {
        return INSTANCE;
    }

    public boolean isLoaded() {
        return ModList.get().isLoaded("apotheosis");
    }

    public boolean hasCharmLikeEffects(final ItemStack stack) {
        final String path = stack.getItem().toString().toLowerCase(java.util.Locale.ROOT);
        return path.contains("charm");
    }
}
