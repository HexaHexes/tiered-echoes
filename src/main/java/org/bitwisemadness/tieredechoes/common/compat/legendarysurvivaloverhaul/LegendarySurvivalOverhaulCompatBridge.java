package org.bitwisemadness.tieredechoes.common.compat.legendarysurvivaloverhaul;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

public final class LegendarySurvivalOverhaulCompatBridge {
    private static final LegendarySurvivalOverhaulCompatBridge INSTANCE = new LegendarySurvivalOverhaulCompatBridge();

    private LegendarySurvivalOverhaulCompatBridge() {
    }

    public static LegendarySurvivalOverhaulCompatBridge getInstance() {
        return INSTANCE;
    }

    public boolean isLoaded() {
        return ModList.get().isLoaded("legendarysurvivaloverhaul");
    }

    public double thermalResistanceAmplifier(final ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("TieredEchoes") ? 1.0D + (stack.getTag().getCompound("TieredEchoes").getInt("tier_sort_order") * 0.05D) : 1.0D;
    }
}
