package org.bitwisemadness.tieredechoes.common.compat.sophisticatedbackpacks;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

public final class SophisticatedBackpacksCompatBridge {
    private static final SophisticatedBackpacksCompatBridge INSTANCE = new SophisticatedBackpacksCompatBridge();

    private SophisticatedBackpacksCompatBridge() {
    }

    public static SophisticatedBackpacksCompatBridge getInstance() {
        return INSTANCE;
    }

    public boolean isLoaded() {
        return ModList.get().isLoaded("sophisticatedbackpacks");
    }

    public boolean isBackpack(final ItemStack stack) {
        return stack.getItem().toString().toLowerCase(java.util.Locale.ROOT).contains("backpack");
    }
}
