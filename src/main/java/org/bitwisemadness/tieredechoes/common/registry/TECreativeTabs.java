package org.bitwisemadness.tieredechoes.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.client.creative.CreativePreviewCatalog;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TECreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TieredEchoes.MOD_ID);
    public static final Map<TierDomain, RegistryObject<CreativeModeTab>> DOMAIN_TABS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<CreativeModeTab>> CURIOS_TABS = new LinkedHashMap<>();
    public static final RegistryObject<CreativeModeTab> TEMPLATES_TAB = registerTemplatesTab();

    static {
        registerDomainTab(TierDomain.TOOL, Items.DIAMOND_PICKAXE);
        registerDomainTab(TierDomain.WEAPON, Items.DIAMOND_SWORD);
        registerDomainTab(TierDomain.ARMOR, Items.DIAMOND_CHESTPLATE);
        if (ModList.get().isLoaded("curios")) {
            registerDomainTab(TierDomain.ACCESSORY, Items.TOTEM_OF_UNDYING);
        }
    }

    private TECreativeTabs() {
    }

    private static RegistryObject<CreativeModeTab> registerTemplatesTab() {
        return TABS.register("templates", () -> {
            final CreativeModeTab.Builder builder = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tieredechoes.templates"))
                    .icon(() -> new ItemStack(TEItems.UPGRADE_TEMPLATES.get("common").get()))
                    .displayItems((parameters, output) -> {
                        TEItems.UPGRADE_TEMPLATES.values().forEach(template -> output.accept(template.get()));
                        TEItems.DOWNGRADE_TEMPLATES.values().forEach(template -> output.accept(template.get()));
                    });
            templatePreviousTabKey().ifPresent(builder::withTabsAfter);
            return builder.build();
        });
    }

    private static void registerDomainTab(final TierDomain domain, final net.minecraft.world.item.Item iconItem) {
        DOMAIN_TABS.put(domain, TABS.register(domain.getSerializedName(), () -> {
            final CreativeModeTab.Builder builder = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tieredechoes.domain." + domain.getSerializedName()))
                    .icon(() -> new ItemStack(iconItem))
                    .displayItems((parameters, output) -> CreativePreviewCatalog.populateDomain(domain, output));
            previousTabKey(domain).ifPresent(builder::withTabsAfter);
            return builder.build();
        }));
    }

    private static java.util.Optional<ResourceKey<CreativeModeTab>> previousTabKey(final TierDomain domain) {
        return switch (domain) {
            case WEAPON -> java.util.Optional.of(tabKey(TierDomain.TOOL));
            case ARMOR -> java.util.Optional.of(tabKey(TierDomain.WEAPON));
            case ACCESSORY -> java.util.Optional.of(tabKey(TierDomain.ARMOR));
            default -> java.util.Optional.empty();
        };
    }

    private static java.util.Optional<ResourceKey<CreativeModeTab>> templatePreviousTabKey() {
        if (ModList.get().isLoaded("curios")) {
            return java.util.Optional.of(tabKey(TierDomain.ACCESSORY));
        }
        return java.util.Optional.of(tabKey(TierDomain.ARMOR));
    }

    private static ResourceKey<CreativeModeTab> templatesTabKey() {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation(TieredEchoes.MOD_ID, "templates"));
    }

    private static ResourceKey<CreativeModeTab> tabKey(final TierDomain domain) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation(TieredEchoes.MOD_ID, domain.getSerializedName()));
    }

}
