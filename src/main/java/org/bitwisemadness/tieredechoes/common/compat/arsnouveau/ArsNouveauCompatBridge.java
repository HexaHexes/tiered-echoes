package org.bitwisemadness.tieredechoes.common.compat.arsnouveau;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

public final class ArsNouveauCompatBridge {
    private static final ArsNouveauCompatBridge INSTANCE = new ArsNouveauCompatBridge();

    private ArsNouveauCompatBridge() {
    }

    public static ArsNouveauCompatBridge getInstance() {
        return INSTANCE;
    }

    public boolean isLoaded() {
        return ModList.get().isLoaded("ars_nouveau");
    }

    public boolean supportsThreads(final ItemStack stack) {
        final String path = stack.getItem().toString().toLowerCase(java.util.Locale.ROOT);
        return path.contains("robe") || path.contains("armor") || path.contains("thread");
    }
}
