package org.bitwisemadness.tieredechoes.common.compat.curios;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CuriosCompatBridge {
    private static final CuriosCompatBridge INSTANCE = new CuriosCompatBridge();
    private final Set<String> discoveredSlotTypes = new LinkedHashSet<>();

    private CuriosCompatBridge() {
        if (this.isLoaded()) {
            this.discoveredSlotTypes.add("ring");
            this.discoveredSlotTypes.add("charm");
            this.discoveredSlotTypes.add("belt");
            this.discoveredSlotTypes.add("necklace");
            this.discoveredSlotTypes.add("back");
            this.discoveredSlotTypes.add("bracelet");
            this.discoveredSlotTypes.add("head");
        }
    }

    public static CuriosCompatBridge getInstance() {
        return INSTANCE;
    }

    public boolean isLoaded() {
        return ModList.get().isLoaded("curios");
    }

    public Set<String> getRegisteredSlotTypes() {
        return Set.copyOf(this.discoveredSlotTypes);
    }

    public Set<String> getSupportedSlotTypes(final Item item) {
        final String path = BuiltInRegistries.ITEM.getKey(item).getPath().toLowerCase(Locale.ROOT);
        final Set<String> slots = new LinkedHashSet<>();
        for (final String slot : this.discoveredSlotTypes) {
            if (path.contains(slot)) {
                slots.add(slot);
            }
        }
        if (path.contains("amulet") || path.contains("necklace")) {
            slots.add("necklace");
        }
        if (path.contains("ring")) {
            slots.add("ring");
        }
        if (path.contains("charm") || path.contains("totem")) {
            slots.add("charm");
        }
        if (path.contains("belt")) {
            slots.add("belt");
        }
        if (path.contains("elytra") || path.contains("backpack") || path.contains("cape")) {
            slots.add("back");
        }
        return Set.copyOf(slots);
    }

    public boolean supportsAnySlot(final Item item) {
        return !this.getSupportedSlotTypes(item).isEmpty();
    }

    public List<ItemStack> getEquippedCurios(final LivingEntity entity) {
        // Reflection-based Curios access can be added later; keep a safe empty fallback for now.
        return List.of();
    }

    public Set<String> getEquippedSlotTypes(final ItemStack stack) {
        return this.getSupportedSlotTypes(stack.getItem());
    }

    public List<String> getSlotTypesWithItems(final LivingEntity entity) {
        final List<String> slots = new ArrayList<>();
        for (final ItemStack stack : getEquippedCurios(entity)) {
            slots.addAll(getEquippedSlotTypes(stack));
        }
        return List.copyOf(slots);
    }
}
