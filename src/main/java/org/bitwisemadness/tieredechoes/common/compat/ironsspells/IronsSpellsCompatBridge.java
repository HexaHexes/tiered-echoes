package org.bitwisemadness.tieredechoes.common.compat.ironsspells;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

public final class IronsSpellsCompatBridge {
    private static final IronsSpellsCompatBridge INSTANCE = new IronsSpellsCompatBridge();

    private IronsSpellsCompatBridge() {
    }

    public static IronsSpellsCompatBridge getInstance() {
        return INSTANCE;
    }

    public boolean isLoaded() {
        return ModList.get().isLoaded("irons_spellbooks");
    }

    public boolean supportsSpellAttributes(final ItemStack stack) {
        final String path = stack.getItem().toString().toLowerCase(java.util.Locale.ROOT);
        return path.contains("staff") || path.contains("spell") || path.contains("robe") || path.contains("wand");
    }
}
