package org.bitwisemadness.tieredechoes.common.tier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.SwordItem;
import org.bitwisemadness.tieredechoes.api.common.ITierService;

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
import java.util.Locale;

public final class TierService implements ITierService {
    private static final TierService INSTANCE = new TierService();
    private final Map<TierDomain, List<TierDefinition>> tiersByDomain = new EnumMap<>(TierDomain.class);
    private final Map<TierKey, TierDefinition> tiersByKey = new LinkedHashMap<>();

    private TierService() {
        this.replaceAll(this.builtinDefaults());
    }

    public static TierService getInstance() {
        return INSTANCE;
    }

    public synchronized void replaceAll(final Collection<TierDefinition> definitions) {
        this.tiersByDomain.clear();
        this.tiersByKey.clear();
        for (final TierDefinition definition : definitions) {
            this.tiersByKey.put(TierKey.of(definition), definition);
            this.tiersByDomain.computeIfAbsent(definition.domain(), key -> new ArrayList<>()).add(definition);
        }
        for (final List<TierDefinition> values : this.tiersByDomain.values()) {
            values.sort(Comparator.comparingInt(TierDefinition::sortOrder).thenComparing(def -> def.id().toString()));
        }
    }

    @Override
    public synchronized List<Item> collectPreviewItems(final TierDomain domain) {
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
    public synchronized List<TierDefinition> getOrderedValidTiersForItem(final Item item, final TierDomain domain) {
        if (!this.inferDomains(item).contains(domain)) {
            return List.of();
        }
        return this.getOrderedTiers(domain);
    }

    public synchronized List<TierDefinition> getOrderedTiers(final TierDomain domain) {
        return List.copyOf(this.tiersByDomain.getOrDefault(domain, List.of()));
    }

    /**
     * Domain-scoped lookup. This is the only correct way to resolve a tier id, because tier ids are
     * shared across domains.
     */
    public synchronized Optional<TierDefinition> getTier(final ResourceLocation tierId, final TierDomain domain) {
        if (tierId == null || domain == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.tiersByKey.get(new TierKey(domain, tierId)));
    }

    /**
     * Unscoped lookup kept for API compatibility. Ambiguous whenever more than one domain declares
     * the id, so it returns empty in that case rather than an arbitrary winner.
     */
    @Override
    public synchronized Optional<TierDefinition> getTier(final ResourceLocation tierId) {
        if (tierId == null) {
            return Optional.empty();
        }
        TierDefinition found = null;
        for (final TierDefinition definition : this.tiersByKey.values()) {
            if (!definition.id().equals(tierId)) {
                continue;
            }
            if (found != null) {
                return Optional.empty();
            }
            found = definition;
        }
        return Optional.ofNullable(found);
    }

    public synchronized Optional<TierDefinition> getInitialTierForItem(final Item item, final TierDomain preferredDomain) {
        if (preferredDomain == null || !this.inferDomains(item).contains(preferredDomain)) {
            return Optional.empty();
        }
        final List<TierDefinition> tiers = this.tiersByDomain.getOrDefault(preferredDomain, List.of());
        return tiers.isEmpty() ? Optional.empty() : Optional.of(tiers.get(0));
    }

    public synchronized Optional<TierDefinition> getPreviousTier(final TierDefinition tier) {
        if (tier.previousTier().isPresent()) {
            return this.getTier(tier.previousTier().get(), tier.domain());
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
        if (item instanceof PickaxeItem || item instanceof ShovelItem || item instanceof AxeItem || item instanceof HoeItem) {
            domains.add(TierDomain.TOOL);
        }
        if (item instanceof ArmorItem) {
            domains.add(TierDomain.ARMOR);
        }
        if (isAccessoryItem(item)) {
            domains.add(TierDomain.ACCESSORY);
        }
        return Set.copyOf(domains);
    }

    public synchronized Map<String, Attribute> getScalableAttributes(final Item item) {
        final Map<String, Attribute> keys = new LinkedHashMap<>();
        collectAttributeMap(item, EquipmentSlot.MAINHAND, keys);
        collectAttributeMap(item, EquipmentSlot.HEAD, keys);
        collectAttributeMap(item, EquipmentSlot.CHEST, keys);
        collectAttributeMap(item, EquipmentSlot.LEGS, keys);
        collectAttributeMap(item, EquipmentSlot.FEET, keys);
        return Map.copyOf(keys);
    }

    private void collectAttributeMap(final Item item, final EquipmentSlot slot, final Map<String, Attribute> keys) {
        for (final Attribute attribute : item.getDefaultAttributeModifiers(slot).keySet()) {
            final String key = getMultiplierKey(attribute);
            if (key != null) {
                keys.putIfAbsent(key, attribute);
            }
        }
    }

    public String getMultiplierKey(final Attribute attribute) {
        if (attribute.equals(Attributes.ATTACK_DAMAGE)) return "attack_damage";
        if (attribute.equals(Attributes.ATTACK_SPEED)) return "attack_speed";
        if (attribute.equals(Attributes.ARMOR)) return "armor";
        if (attribute.equals(Attributes.ARMOR_TOUGHNESS)) return "armor_toughness";
        if (attribute.equals(Attributes.KNOCKBACK_RESISTANCE)) return "knockback_resistance";
        final ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getKey(attribute);
        if (key == null) return null;
        final String namespace = key.getNamespace().toLowerCase(Locale.ROOT);
        final String path = key.getPath().toLowerCase(Locale.ROOT);
        final String description = attribute.getDescriptionId().toLowerCase(Locale.ROOT);
        final String combined = namespace + ":" + path + ":" + description;
        final String compact = combined.replace("_", "").replace(".", "").replace("-", "");
        if (combined.contains("mana") && (combined.contains("regen") || combined.contains("regeneration") || compact.contains("manaregen"))) return "mana_regen";
        if ((combined.contains("mana") && (combined.contains("max") || combined.contains("maximum"))) || compact.contains("maxmana") || compact.contains("maximummana")) return "max_mana";
        if (isColdResistanceAttribute(namespace, combined, compact)) return "cold_resistance";
        if (isHeatResistanceAttribute(namespace, combined, compact)) return "heat_resistance";
        if (isThermalResistanceAttribute(namespace, combined, compact)) return "thermal_resistance";
        return null;
    }


    public Optional<Double> getAttributeMultiplier(final TierDefinition tier, final Attribute attribute) {
        for (final String candidate : getMultiplierKeyCandidates(attribute)) {
            final Double direct = tier.attributeMultipliers().get(candidate);
            if (direct != null) {
                return Optional.of(direct);
            }
        }
        final Map<String, Double> normalizedMultipliers = new LinkedHashMap<>();
        for (final Map.Entry<String, Double> entry : tier.attributeMultipliers().entrySet()) {
            normalizedMultipliers.put(normalizeMultiplierKey(entry.getKey()), entry.getValue());
        }
        for (final String candidate : getMultiplierKeyCandidates(attribute)) {
            final Double normalized = normalizedMultipliers.get(normalizeMultiplierKey(candidate));
            if (normalized != null) {
                return Optional.of(normalized);
            }
        }
        return Optional.empty();
    }

    public Set<String> getMultiplierKeyCandidates(final Attribute attribute) {
        final Set<String> candidates = new LinkedHashSet<>();
        final String canonical = getMultiplierKey(attribute);
        if (canonical != null) {
            candidates.add(canonical);
        }
        final ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getKey(attribute);
        if (id != null) {
            candidates.add(id.toString());
            candidates.add(id.getPath());
            candidates.add(id.getNamespace() + "." + id.getPath());
        }
        candidates.add(attribute.getDescriptionId());
        return Set.copyOf(candidates);
    }

    private String normalizeMultiplierKey(final String key) {
        return key.toLowerCase(Locale.ROOT).replace(":", "").replace("_", "").replace(".", "").replace("-", "").replace(" ", "");
    }
    private boolean isLsoAttributeNamespace(final String namespace) {
        return namespace.equals("legendarysurvivaloverhaul")
                || namespace.equals("legendary_survival_overhaul")
                || namespace.equals("legendarysurvival")
                || namespace.equals("legendary_survival")
                || namespace.equals("lso")
                || namespace.contains("legendary") && namespace.contains("survival");
    }

    private boolean isColdResistanceAttribute(final String namespace, final String combined, final String compact) {
        return (isLsoAttributeNamespace(namespace) && (combined.contains("cold") || compact.contains("cold")))
                || compact.contains("coldresistance")
                || compact.contains("coldresist")
                || combined.contains("cold_resistance")
                || combined.contains("cold.resistance");
    }

    private boolean isHeatResistanceAttribute(final String namespace, final String combined, final String compact) {
        return (isLsoAttributeNamespace(namespace) && (combined.contains("heat") || compact.contains("heat")))
                || compact.contains("heatresistance")
                || compact.contains("heatresist")
                || combined.contains("heat_resistance")
                || combined.contains("heat.resistance");
    }

    private boolean isThermalResistanceAttribute(final String namespace, final String combined, final String compact) {
        return (isLsoAttributeNamespace(namespace) && (combined.contains("thermal") || combined.contains("temperature") || compact.contains("thermal") || compact.contains("temperature")))
                || compact.contains("thermalresistance")
                || compact.contains("thermalresist")
                || compact.contains("temperatureresistance")
                || compact.contains("temperatureresist")
                || combined.contains("thermal_resistance")
                || combined.contains("temperature_resistance")
                || combined.contains("thermal.resistance")
                || combined.contains("temperature.resistance");
    }

    private boolean isAccessoryItem(final Item item) {
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof PickaxeItem || item instanceof ShovelItem || item instanceof HoeItem || item instanceof ArmorItem) {
            return false;
        }
        if (!hasCuriosSlotTag(item)) {
            return false;
        }
        return hasSupportedAccessoryStats(item);
    }

    private boolean hasCuriosSlotTag(final Item item) {
        try {
            return BuiltInRegistries.ITEM.wrapAsHolder(item)
                    .tags()
                    .map(TagKey::location)
                    .anyMatch(location -> "curios".equals(location.getNamespace()));
        } catch (RuntimeException exception) {
            final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            org.bitwisemadness.tieredechoes.TieredEchoes.LOGGER.debug("[TierService] Could not inspect Curios item tags for {}", id, exception);
            return false;
        }
    }

    private boolean hasSupportedAccessoryStats(final Item item) {
        final Set<String> configuredKeys = new LinkedHashSet<>();
        for (final TierDefinition tier : this.tiersByDomain.getOrDefault(TierDomain.ACCESSORY, List.of())) {
            configuredKeys.addAll(tier.attributeMultipliers().keySet());
        }
        for (final Attribute attribute : getScalableAttributes(item).values()) {
            for (final String candidate : getMultiplierKeyCandidates(attribute)) {
                if (configuredKeys.contains(candidate) || configuredKeys.stream().map(this::normalizeMultiplierKey).anyMatch(normalizeMultiplierKey(candidate)::equals)) {
                    return true;
                }
            }
        }
        return isKnownArsManaCurioItem(item) || isKnownLsoResistanceCurioItem(item);
    }

    private boolean isSupportedAccessoryAttributeKey(final String key) {
        return key.equals("attack_damage")
                || key.equals("attack_speed")
                || key.equals("armor")
                || key.equals("armor_toughness")
                || key.equals("knockback_resistance")
                || key.equals("max_mana")
                || key.equals("mana_regen")
                || key.equals("cold_resistance")
                || key.equals("heat_resistance")
                || key.equals("thermal_resistance");
    }

    private boolean isKnownArsManaCurioItem(final Item item) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null || !"ars_nouveau".equals(id.getNamespace())) {
            return false;
        }
        final String path = id.getPath().toLowerCase(Locale.ROOT);
        final boolean likelyCurio = path.contains("ring") || path.contains("amulet") || path.contains("charm") || path.contains("belt") || path.contains("bauble") || path.contains("focus");
        final boolean likelyMana = path.contains("mana") || path.contains("regen");
        return likelyCurio && likelyMana;
    }

    private boolean isKnownLsoResistanceCurioItem(final Item item) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null || !isLsoAttributeNamespace(id.getNamespace().toLowerCase(Locale.ROOT))) {
            return false;
        }
        final String path = id.getPath().toLowerCase(Locale.ROOT);
        final boolean likelyCurio = path.contains("ring") || path.contains("amulet") || path.contains("charm") || path.contains("bracelet") || path.contains("necklace");
        final boolean likelyResistance = path.contains("cold") || path.contains("heat") || path.contains("thermal") || path.contains("temperature");
        return likelyCurio && likelyResistance;
    }

    public synchronized List<TierDefinition> getAllTiers() {
        return List.copyOf(this.tiersByKey.values());
    }

    public synchronized Optional<TierDefinition> resolveCurrentTier(final ItemStack stack, final TierDomain preferredDomain) {
        final Optional<TierDefinition> fromNbt = this.readTierFromNbt(stack, null);
        if (fromNbt.isPresent()) {
            return fromNbt;
        }
        if (stack.hasTag() && stack.getTag().contains("TieredEchoes")
                && stack.getTag().getCompound("TieredEchoes").contains("tier_id")) {
            // The id is recorded but no domain resolves it: the datapack that defined it is gone.
            return Optional.empty();
        }
        final TierDomain domain = preferredDomain != null
                ? preferredDomain
                : this.inferDomains(stack.getItem()).stream().findFirst().orElse(null);
        if (domain == null) {
            return Optional.empty();
        }
        return this.getInitialTierForItem(stack.getItem(), domain);
    }

    /**
     * Reads {@code tier_id} together with the {@code tier_domain} recorded alongside it. Falls back to
     * inferring the domain from the item when the tag predates domain bookkeeping.
     */
    public synchronized Optional<TierDefinition> readTierFromNbt(final ItemStack stack, final TierDomain preferredDomain) {
        if (stack.isEmpty() || !stack.hasTag() || !stack.getTag().contains("TieredEchoes")) {
            return Optional.empty();
        }
        final CompoundTag te = stack.getTag().getCompound("TieredEchoes");
        if (!te.contains("tier_id")) {
            return Optional.empty();
        }
        final ResourceLocation tierId = ResourceLocation.tryParse(te.getString("tier_id"));
        if (tierId == null) {
            return Optional.empty();
        }
        if (te.contains("tier_domain")) {
            try {
                final TierDomain stored = TierDomain.valueOf(te.getString("tier_domain").toUpperCase(Locale.ROOT));
                final Optional<TierDefinition> scoped = this.getTier(tierId, stored);
                if (scoped.isPresent()) {
                    return scoped;
                }
            } catch (final IllegalArgumentException ignored) {
                // Unknown domain written by a newer pack; fall through to inference.
            }
        }
        if (preferredDomain != null) {
            final Optional<TierDefinition> preferred = this.getTier(tierId, preferredDomain);
            if (preferred.isPresent()) {
                return preferred;
            }
        }
        for (final TierDomain domain : this.inferDomains(stack.getItem())) {
            final Optional<TierDefinition> inferred = this.getTier(tierId, domain);
            if (inferred.isPresent()) {
                return inferred;
            }
        }
        return Optional.empty();
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
        items.sort(this.itemComparatorFor(domain));
        return List.copyOf(items);
    }

    private Comparator<Item> itemComparatorFor(final TierDomain domain) {
        return Comparator
                .comparingInt((Item item) -> isVanillaOrderedItem(domain, item) ? 0 : 1)
                .thenComparingInt(item -> isVanillaOrderedItem(domain, item) ? materialRank(domain, item) : 0)
                .thenComparingInt(item -> isVanillaOrderedItem(domain, item) ? subtypeRank(domain, item) : 0)
                .thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace())
                .thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).getPath());
    }

    private boolean isVanillaOrderedItem(final TierDomain domain, final Item item) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id != null && "minecraft".equals(id.getNamespace()) && materialRank(domain, item) < 100;
    }

    private int materialRank(final TierDomain domain, final Item item) {
        final String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        return switch (domain) {
            case TOOL, WEAPON -> {
                if (path.startsWith("wooden_")) yield 0;
                if (path.startsWith("stone_")) yield 1;
                if (path.startsWith("iron_")) yield 2;
                if (path.startsWith("golden_")) yield 3;
                if (path.startsWith("diamond_")) yield 4;
                if (path.startsWith("netherite_")) yield 5;
                yield 100;
            }
            case ARMOR -> {
                if (path.startsWith("leather_")) yield 0;
                if (path.startsWith("chainmail_")) yield 1;
                if (path.startsWith("iron_")) yield 2;
                if (path.startsWith("golden_")) yield 3;
                if (path.startsWith("diamond_")) yield 4;
                if (path.startsWith("netherite_")) yield 5;
                if (path.equals("turtle_helmet")) yield 6;
                yield 100;
            }
            default -> 100;
        };
    }

    private int subtypeRank(final TierDomain domain, final Item item) {
        final String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        return switch (domain) {
            case TOOL -> {
                if (path.endsWith("_pickaxe")) yield 0;
                if (path.endsWith("_axe")) yield 1;
                if (path.endsWith("_shovel")) yield 2;
                if (path.endsWith("_hoe")) yield 3;
                yield 100;
            }
            case WEAPON -> {
                if (path.endsWith("_sword")) yield 0;
                if (path.endsWith("_axe")) yield 1;
                yield 100;
            }
            case ARMOR -> {
                if (path.endsWith("_helmet") || path.equals("turtle_helmet")) yield 0;
                if (path.endsWith("_chestplate")) yield 1;
                if (path.endsWith("_leggings")) yield 2;
                if (path.endsWith("_boots")) yield 3;
                yield 100;
            }
            default -> 100;
        };
    }

    /**
     * The built-in tier ladder. The datapack reloader seeds from this list rather than from the
     * currently applied state, so removing a datapack tier actually removes it on the next reload
     * instead of being carried forward forever.
     */
    public synchronized List<TierDefinition> builtinDefaults() {
        final List<TierDefinition> defaults = new ArrayList<>();
        int schema = 1;
        for (final TierDomain domain : TierDomain.builtinDomains()) {
            defaults.add(new TierDefinition(new ResourceLocation("tieredechoes", "common"), domain, schema, Rarity.COMMON, 0, 0, new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", domain == TierDomain.ACCESSORY ? "gold_ingot" : "cobblestone")), Optional.of(new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", "stick"))), Optional.empty(), Optional.of("builtin"), Map.ofEntries(
                    Map.entry("durability", 1.0D),
                    Map.entry("attack_damage", 1.0D),
                    Map.entry("attack_speed", 1.0D),
                    Map.entry("armor", 1.0D),
                    Map.entry("armor_toughness", 1.0D),
                    Map.entry("knockback_resistance", 1.0D),
                    Map.entry("max_mana", 1.0D),
                    Map.entry("mana_regen", 1.0D),
                    Map.entry("cold_resistance", 1.0D),
                    Map.entry("heat_resistance", 1.0D),
                    Map.entry("thermal_resistance", 1.0D)
            ), Map.of("*2", 1.05D), Map.of(), true, true));
            defaults.add(new TierDefinition(new ResourceLocation("tieredechoes", "uncommon"), domain, schema, Rarity.UNCOMMON, 1, 25, new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", domain == TierDomain.ACCESSORY ? "emerald" : "iron_ingot")), Optional.of(new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", domain == TierDomain.ACCESSORY ? "gold_ingot" : "cobblestone"))), Optional.of(new ResourceLocation("tieredechoes", "common")), Optional.of("builtin"), Map.ofEntries(
                    Map.entry("durability", 1.1D),
                    Map.entry("attack_damage", 1.12D),
                    Map.entry("attack_speed", 1.04D),
                    Map.entry("armor", 1.12D),
                    Map.entry("armor_toughness", 1.1D),
                    Map.entry("knockback_resistance", 1.08D),
                    Map.entry("max_mana", 1.12D),
                    Map.entry("mana_regen", 1.08D),
                    Map.entry("cold_resistance", 1.12D),
                    Map.entry("heat_resistance", 1.12D),
                    Map.entry("thermal_resistance", 1.12D)
            ), Map.of("*2", 1.07D, "*3", 1.1D), Map.of(), true, true));
            defaults.add(new TierDefinition(new ResourceLocation("tieredechoes", "rare"), domain, schema, Rarity.RARE, 2, 75, new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", "diamond")), Optional.of(new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", domain == TierDomain.ACCESSORY ? "emerald" : "iron_ingot"))), Optional.of(new ResourceLocation("tieredechoes", "uncommon")), Optional.of("builtin"), Map.ofEntries(
                    Map.entry("durability", 1.2D),
                    Map.entry("attack_damage", 1.25D),
                    Map.entry("attack_speed", 1.08D),
                    Map.entry("armor", 1.25D),
                    Map.entry("armor_toughness", 1.2D),
                    Map.entry("knockback_resistance", 1.15D),
                    Map.entry("max_mana", 1.25D),
                    Map.entry("mana_regen", 1.15D),
                    Map.entry("cold_resistance", 1.25D),
                    Map.entry("heat_resistance", 1.25D),
                    Map.entry("thermal_resistance", 1.25D)
            ), Map.of("*2", 1.1D, "*3", 1.15D, "*4", 1.2D), Map.of(), true, true));
            defaults.add(new TierDefinition(new ResourceLocation("tieredechoes", "epic"), domain, schema, Rarity.EPIC, 3, 150, new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", domain == TierDomain.ACCESSORY ? "nether_star" : "netherite_ingot")), Optional.of(new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", "diamond"))), Optional.of(new ResourceLocation("tieredechoes", "rare")), Optional.of("builtin"), Map.ofEntries(
                    Map.entry("durability", 1.35D),
                    Map.entry("attack_damage", 1.4D),
                    Map.entry("attack_speed", 1.12D),
                    Map.entry("armor", 1.4D),
                    Map.entry("armor_toughness", 1.35D),
                    Map.entry("knockback_resistance", 1.25D),
                    Map.entry("max_mana", 1.4D),
                    Map.entry("mana_regen", 1.25D),
                    Map.entry("cold_resistance", 1.4D),
                    Map.entry("heat_resistance", 1.4D),
                    Map.entry("thermal_resistance", 1.4D)
            ), Map.of("*2", 1.12D, "*3", 1.18D, "*4", 1.24D), Map.of(), true, true));
        }
        return List.copyOf(defaults);
    }
}