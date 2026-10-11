package org.bitwisemadness.tieredechoes.common.tier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bitwisemadness.tieredechoes.api.common.ITierService;
import org.bitwisemadness.tieredechoes.common.registry.TieredEchoesTagKeys;

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
        // Empty until the first datapack reload. What ships inside the jar is the generated
        // datapack, not a Java table; keeping a second copy here would mean a datapack could
        // never actually remove a tier, because the fallback would put it straight back.
        this.replaceAll(List.of());
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

    /**
     * The items that can be previewed in a domain's creative tab: every registered item the domain's
     * tag claims. There is no per-domain branch because there is no per-domain membership rule — the
     * tag is the whole of it.
     */
    @Override
    public synchronized List<Item> collectPreviewItems(final TierDomain domain) {
        return registeredItemsFor(domain);
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

    /**
     * The domains this item has a ladder in, decided solely by the {@code tieredechoes:<name>} item
     * tags.
     *
     * <p>Membership is deliberately exclusive to the tags — there is no {@code instanceof} fallback,
     * because a heuristic and a tag can disagree and then the answer depends on load order. An item
     * in two tags belongs to two domains, so an axe is both a weapon and a tool and climbs each
     * ladder independently.</p>
     *
     * <p>Iteration follows enum declaration order so that the result is deterministic. Callers such
     * as {@link #resolveCurrentTier} pick the first element, which must not change between runs.</p>
     */
    public synchronized Set<TierDomain> inferDomains(final Item item) {
        if (item == null) {
            return Set.of();
        }
        final Set<TierDomain> domains = new LinkedHashSet<>();
        for (final TierDomain domain : TierDomain.values()) {
            final TagKey<Item> tag = TieredEchoesTagKeys.forDomain(domain).orElse(null);
            if (tag != null && item.builtInRegistryHolder().is(tag)) {
                domains.add(domain);
            }
        }
        // Set.copyOf would discard the insertion order, so wrap instead.
        return java.util.Collections.unmodifiableSet(domains);
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

    /**
     * Resolves a multiplier by plain string key, for the things that scale but are not attributes —
     * durability, and later enchantability.
     *
     * <p>Shares the normalisation fallback with {@link #getAttributeMultiplier} so a datapack can
     * spell a key as {@code durability}, {@code Durability} or {@code minecraft:durability} and get
     * the same answer from either path.</p>
     */
    public Optional<Double> getScalarMultiplier(final TierDefinition tier, final String key) {
        if (tier == null || key == null) {
            return Optional.empty();
        }
        final Double direct = tier.attributeMultipliers().get(key);
        if (direct != null) {
            return Optional.of(direct);
        }
        final String normalized = normalizeMultiplierKey(key);
        for (final Map.Entry<String, Double> entry : tier.attributeMultipliers().entrySet()) {
            if (normalizeMultiplierKey(entry.getKey()).equals(normalized)) {
                return Optional.of(entry.getValue());
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
            // These domains have no material ladder in vanilla — one item each, or two where the
            // second is strictly better — so the rank only has to keep them in a sensible order.
            case SHIELD -> path.equals("shield") ? 0 : 100;
            case ELYTRA -> path.equals("elytra") ? 0 : 100;
            case RANGED_WEAPONS -> {
                if (path.equals("bow")) yield 0;
                if (path.equals("crossbow")) yield 1;
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
}