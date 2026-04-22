package org.bitwisemadness.tieredechoes.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.client.creative.CreativePreviewCatalog;
import org.bitwisemadness.tieredechoes.common.compat.curios.CuriosCompatBridge;
import org.bitwisemadness.tieredechoes.common.compat.sophisticatedbackpacks.SophisticatedBackpacksCompatBridge;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TECreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TieredEchoes.MOD_ID);
    public static final Map<TierDomain, RegistryObject<CreativeModeTab>> DOMAIN_TABS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<CreativeModeTab>> CURIOS_TABS = new LinkedHashMap<>();

    static {
        registerDomainTab(TierDomain.WEAPON, Items.DIAMOND_SWORD);
        registerDomainTab(TierDomain.TOOL, Items.DIAMOND_PICKAXE);
        registerDomainTab(TierDomain.ARMOR, Items.DIAMOND_CHESTPLATE);
        registerDomainTab(TierDomain.ELYTRA, Items.ELYTRA);

        if (SophisticatedBackpacksCompatBridge.getInstance().isLoaded()) {
            registerDomainTab(TierDomain.STORAGE, Items.CHEST);
        }

        if (CuriosCompatBridge.getInstance().isLoaded()) {
            registerDomainTab(TierDomain.ACCESSORY, Items.TOTEM_OF_UNDYING);

            for (final String slotType : CuriosCompatBridge.getInstance().getRegisteredSlotTypes()) {
                CURIOS_TABS.put(slotType, TABS.register("curios_" + slotType, () -> CreativeModeTab.builder()
                        .title(Component.translatable("tab.tieredechoes.curios." + slotType))
                        .icon(() -> new ItemStack(iconForCuriosSlot(slotType)))
                        .displayItems((parameters, output) -> CreativePreviewCatalog.populateCuriosSlot(slotType, output))
                        .build()));
            }
        }
    }

    private TECreativeTabs() {
    }

    private static void registerDomainTab(final TierDomain domain, final net.minecraft.world.item.Item iconItem) {
        DOMAIN_TABS.put(domain, TABS.register(domain.getSerializedName(), () -> CreativeModeTab.builder()
                .title(Component.translatable("tab.tieredechoes.domain." + domain.getSerializedName()))
                .icon(() -> new ItemStack(iconItem))
                .displayItems((parameters, output) -> CreativePreviewCatalog.populateDomain(domain, output))
                .build()));
    }

    private static net.minecraft.world.item.Item iconForCuriosSlot(final String slotType) {
        return switch (slotType) {
            case "charm" -> Items.TOTEM_OF_UNDYING;
            case "belt" -> Items.LEAD;
            case "necklace" -> Items.AMETHYST_SHARD;
            case "back" -> Items.ELYTRA;
            case "bracelet" -> Items.GOLD_INGOT;
            default -> Items.GOLD_NUGGET;
        };
    }
}
