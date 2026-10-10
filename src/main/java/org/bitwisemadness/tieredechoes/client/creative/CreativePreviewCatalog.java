package org.bitwisemadness.tieredechoes.client.creative;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.bitwisemadness.tieredechoes.common.upgrade.preview.PreviewUpgradeService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class CreativePreviewCatalog {
    private static final CreativePreviewCatalog INSTANCE = new CreativePreviewCatalog();

    private CreativePreviewCatalog() {
    }

    public static CreativePreviewCatalog getInstance() {
        return INSTANCE;
    }

    public static void populateDomain(final TierDomain domain, final CreativeModeTab.Output output) {
        final List<Item> items = List.copyOf(TierService.getInstance().collectPreviewItems(domain));
        for (final Item item : items) {
            final List<TierDefinition> tiers = TierService.getInstance().getOrderedValidTiersForItem(item, domain);
            for (final TierDefinition tier : tiers) {
                final ItemStack stack = PreviewUpgradeService.getInstance().createPreviewStack(new ItemStack(item), tier);
                output.accept(stack);
            }
        }
    }

    public List<CreativePreviewEntry> getEntriesForTab(final String tabKey) {
        if (tabKey == null || tabKey.isBlank()) {
            return List.of();
        }

        final TierDomain domain = parseDomain(tabKey);
        if (domain == null) {
            return List.of();
        }
        return this.buildDomainEntries(domain);
    }

    private List<CreativePreviewEntry> buildDomainEntries(final TierDomain domain) {
        final List<CreativePreviewEntry> result = new ArrayList<>();
        final List<Item> items = List.copyOf(TierService.getInstance().collectPreviewItems(domain));
        for (final Item item : items) {
            final Set<TierDomain> domains = TierService.getInstance().resolveDomains(item);
            final List<TierDefinition> tiers = TierService.getInstance().getOrderedValidTiersForItem(item, domain);
            for (final TierDefinition tier : tiers) {
                final ItemStack stack = PreviewUpgradeService.getInstance().createPreviewStack(new ItemStack(item), tier);
                result.add(new CreativePreviewEntry(item, tier, stack, domains));
            }
        }
        return List.copyOf(result);
    }

    private static TierDomain parseDomain(final String tabKey) {
        try {
            return TierDomain.valueOf(tabKey.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return switch (tabKey) {
                case "weapons", "weapon" -> TierDomain.WEAPON;
                case "tools", "tool" -> TierDomain.TOOL;
                case "armor" -> TierDomain.ARMOR;
                case "curios", "curio", "accessory", "accessories" -> TierDomain.ACCESSORY;
                default -> null;
            };
        }
    }
}
