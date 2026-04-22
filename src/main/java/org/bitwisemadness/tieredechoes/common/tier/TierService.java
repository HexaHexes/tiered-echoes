package org.bitwisemadness.tieredechoes.common.tier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import org.bitwisemadness.tieredechoes.api.common.ITierService;
import org.bitwisemadness.tieredechoes.common.compat.curios.CuriosCompatBridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TierService implements ITierService {
    private static final TierService INSTANCE = new TierService();
    private final Map<TierDomain, List<TierDefinition>> tiersByDomain = new EnumMap<>(TierDomain.class);
    private final Map<ResourceLocation, TierDefinition> tiersById = new LinkedHashMap<>();

    private TierService() {
        this.bootstrapDefaults();
    }

    public static TierService getInstance() {
        return INSTANCE;
    }

    public synchronized void replaceAll(final Collection<TierDefinition> definitions) {
        this.tiersByDomain.clear();
        this.tiersById.clear();
        for (final TierDefinition definition : definitions) {
            this.tiersById.put(new ResourceLocation(definition.domain().getSerializedName(), definition.id().getPath()), definition);
            this.tiersById.put(definition.id(), definition);
            this.tiersByDomain.computeIfAbsent(definition.domain(), key -> new ArrayList<>()).add(definition);
        }
        for (final List<TierDefinition> values : this.tiersByDomain.values()) {
            values.sort(Comparator.comparingInt(TierDefinition::sortOrder).thenComparing(def -> def.id().toString()));
        }
    }

    @Override
    public synchronized Collection<Item> collectPreviewItems(final TierDomain domain) {
        return switch (domain) {
            case WEAPON -> registeredItemsFor(domain);
            case TOOL -> registeredItemsFor(domain);
            case ARMOR -> registeredItemsFor(domain);
            case ELYTRA -> registeredItemsFor(domain);
            case STORAGE -> registeredItemsFor(domain);
            case ACCESSORY -> registeredItemsFor(domain);
        };
    }

    @Override
    public synchronized Collection<Item> collectPreviewItemsForCuriosSlot(final String slotType) {
        final List<Item> items = new ArrayList<>();
        for (final Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            if (CuriosCompatBridge.getInstance().getSupportedSlotTypes(item).contains(slotType) && this.inferDomains(item).contains(TierDomain.ACCESSORY)) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    @Override
    public synchronized List<TierDefinition> getOrderedValidTiersForItem(final Item item, final TierDomain domain) {
        if (!this.inferDomains(item).contains(domain)) {
            return List.of();
        }
        return this.getOrderedTiers(domain);
    }

    public synchronized List<TierDefinition> getOrderedTiers(final TierDomain domain) {
        return List.copyOf(this.tiersByDomain.getOrDefault(domain, List.of()));
    }

    @Override
    public synchronized Optional<TierDefinition> getTier(final ResourceLocation tierId) {
        return Optional.ofNullable(this.tiersById.get(tierId));
    }

    public synchronized Optional<TierDefinition> getInitialTierForItem(final Item item, final TierDomain preferredDomain) {
        if (!this.inferDomains(item).contains(preferredDomain)) {
            return Optional.empty();
        }
        final List<TierDefinition> tiers = this.tiersByDomain.getOrDefault(preferredDomain, List.of());
        return tiers.isEmpty() ? Optional.empty() : Optional.of(tiers.get(0));
    }

    public synchronized Optional<TierDefinition> getPreviousTier(final TierDefinition tier) {
        if (tier.previousTier().isPresent()) {
            return this.getTier(tier.previousTier().get());
        }
        final List<TierDefinition> ordered = this.tiersByDomain.getOrDefault(tier.domain(), List.of());
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id().equals(tier.id()) && i > 0) {
                return Optional.of(ordered.get(i - 1));
            }
        }
        return Optional.empty();
    }


    public synchronized Set<TierDomain> resolveDomains(final Item item) {
        return this.inferDomains(item);
    }

    public synchronized Set<TierDomain> inferDomains(final Item item) {
        final Set<TierDomain> domains = new LinkedHashSet<>();
        if (item instanceof SwordItem || item instanceof AxeItem) {
            domains.add(TierDomain.WEAPON);
        }
        if (item instanceof PickaxeItem || item instanceof ShovelItem || item instanceof AxeItem) {
            domains.add(TierDomain.TOOL);
        }
        if (item instanceof ArmorItem) {
            domains.add(TierDomain.ARMOR);
        }
        if (item instanceof ElytraItem) {
            domains.add(TierDomain.ELYTRA);
        }
        final ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        final String path = key.getPath();
        if (item == Items.CHEST || path.contains("backpack")) {
            domains.add(TierDomain.STORAGE);
        }
        if (CuriosCompatBridge.getInstance().supportsAnySlot(item) || path.contains("ring") || path.contains("charm") || path.contains("amulet") || path.contains("necklace") || path.contains("belt")) {
            domains.add(TierDomain.ACCESSORY);
        }
        return Set.copyOf(domains);
    }

    public synchronized List<TierDefinition> getAllTiers() {
        return List.copyOf(this.tiersById.values().stream().distinct().toList());
    }

    public synchronized Optional<TierDefinition> resolveCurrentTier(final ItemStack stack, final TierDomain preferredDomain) {
        if (stack.hasTag() && stack.getTag().contains("TieredEchoes")) {
            final var te = stack.getTag().getCompound("TieredEchoes");
            if (te.contains("tier_id")) {
                return this.getTier(ResourceLocation.tryParse(te.getString("tier_id")));
            }
        }
        return this.getInitialTierForItem(stack.getItem(), preferredDomain);
    }

    private List<Item> registeredItemsFor(final TierDomain domain) {
        final List<Item> items = new ArrayList<>();
        for (final Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            if (this.inferDomains(item).contains(domain)) {
                items.add(item);
            }
        }
        items.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        return List.copyOf(items);
    }

    private void bootstrapDefaults() {
        final List<TierDefinition> defaults = new ArrayList<>();
        int schema = 1;
        for (final TierDomain domain : TierDomain.values()) {
            defaults.add(new TierDefinition(new ResourceLocation("tieredechoes", "common"), domain, schema, Rarity.COMMON, 0, 0, new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", domain == TierDomain.ACCESSORY ? "gold_ingot" : "cobblestone")), Optional.of(new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", "stick"))), Optional.empty(), Optional.of("builtin"), Map.of("durability", 1.0D), Map.of("*2", 1.05D), Map.of(), true, true));
            defaults.add(new TierDefinition(new ResourceLocation("tieredechoes", "uncommon"), domain, schema, Rarity.UNCOMMON, 1, 25, new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", domain == TierDomain.ACCESSORY ? "emerald" : "iron_ingot")), Optional.of(new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", domain == TierDomain.ACCESSORY ? "gold_ingot" : "cobblestone"))), Optional.of(new ResourceLocation("tieredechoes", "common")), Optional.of("builtin"), Map.of("durability", 1.1D), Map.of("*2", 1.07D, "*3", 1.1D), Map.of(), true, true));
            defaults.add(new TierDefinition(new ResourceLocation("tieredechoes", "rare"), domain, schema, Rarity.RARE, 2, 75, new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", "diamond")), Optional.of(new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", domain == TierDomain.ACCESSORY ? "emerald" : "iron_ingot"))), Optional.of(new ResourceLocation("tieredechoes", "uncommon")), Optional.of("builtin"), Map.of("durability", 1.2D), Map.of("*2", 1.1D, "*3", 1.15D, "*4", 1.2D), Map.of(), true, true));
            defaults.add(new TierDefinition(new ResourceLocation("tieredechoes", "epic"), domain, schema, Rarity.EPIC, 3, 150, new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", domain == TierDomain.ACCESSORY ? "nether_star" : "netherite_ingot")), Optional.of(new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", "diamond"))), Optional.of(new ResourceLocation("tieredechoes", "rare")), Optional.of("builtin"), Map.of("durability", 1.35D), Map.of("*2", 1.12D, "*3", 1.18D, "*4", 1.24D), Map.of(), true, true));
        }
        this.replaceAll(defaults);
    }
}
