package org.bitwisemadness.tieredechoes.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    public static final RegistryObject<CreativeModeTab> TEMPLATES_TAB = registerTemplatesTab();

    /**
     * One tab per domain that ships a ladder, keyed off {@link TierDomain#builtinDomains()} rather
     * than spelled out. A domain that ships no ladder gets no tab, because an empty tab reads as a
     * broken feature rather than as an invitation to write a datapack.
     */
    static {
        registerDomainTab(TierDomain.TOOL, Items.DIAMOND_PICKAXE);
        registerDomainTab(TierDomain.WEAPON, Items.DIAMOND_SWORD);
        registerDomainTab(TierDomain.ARMOR, Items.DIAMOND_CHESTPLATE);
    }

    private TECreativeTabs() {
    }

    private static RegistryObject<CreativeModeTab> registerTemplatesTab() {
        return TABS.register("templates", () -> {
            final CreativeModeTab.Builder builder = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tieredechoes.templates"))
                    .icon(() -> new ItemStack(TEItems.UPGRADE_TEMPLATES.get("common").get()))
                    .displayItems((parameters, output) ->
                            TEItems.UPGRADE_TEMPLATES.values().forEach(template -> output.accept(template.get())));
            // Always last. The accessory tab this used to follow no longer exists, and the curios
            // check that chose it would otherwise have to know about a tab nothing registers.
            builder.withTabsAfter(tabKey(TierDomain.ARMOR));
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

    /**
     * The tab this one should follow. Derived from declaration order rather than a hand-written
     * switch, because the switch silently dropped every domain nobody remembered to add — which is
     * how a domain could ship unlinked from the tabs before it.
     */
    private static java.util.Optional<ResourceKey<CreativeModeTab>> previousTabKey(final TierDomain domain) {
        final java.util.List<TierDomain> order = TierDomain.builtinDomains();
        final int index = order.indexOf(domain);
        if (index <= 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(tabKey(order.get(index - 1)));
    }

    private static ResourceKey<CreativeModeTab> tabKey(final TierDomain domain) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation(TieredEchoes.MOD_ID, domain.getSerializedName()));
    }

}
