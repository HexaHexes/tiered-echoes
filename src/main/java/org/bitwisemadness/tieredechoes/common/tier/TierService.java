package org.bitwisemadness.tieredechoes.common.tier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bitwisemadness.tieredechoes.api.common.ITierService;
import org.bitwisemadness.tieredechoes.common.policy.PolicyService;
import org.bitwisemadness.tieredechoes.common.registry.TieredEchoesTagKeys;

public final class TierService implements ITierService {
    private static final TierService INSTANCE = new TierService();

    /**
     * The NBT keys naming the per-domain ladder state. Held here rather than inlined so the read
     * site and {@code UpgradeService}'s write site cannot drift apart — a mismatch would show up as
     * every tier silently failing to load.
     */
    public static final String ROOT_TAG = "TieredEchoes";
    public static final String DOMAINS_TAG = "domains";
    public static final String TIER_ID_TAG = "tier_id";
    public static final String DISPLAY_RARITY_TAG = "display_rarity";
    public static final String SORT_ORDER_TAG = "tier_sort_order";

    private final Map<TierDomain, List<TierDefinition>> tiersByDomain = new EnumMap<>(TierDomain.class);
    private final Map<TierKey, TierDefinition> tiersByKey = new LinkedHashMap<>();
    private final Object lock = new Object();

    private TierService() {
        // Empty until the first datapack reload. What ships inside the jar is the generated
        // datapack, not a Java table; keeping a second copy here would mean a datapack could
        // never actually remove a tier, because the fallback would put it straight back.
        this.replaceAll(List.of());
    }

    public static TierService getInstance() {
        return INSTANCE;
    }

    public void replaceAll(final Collection<TierDefinition> definitions) {
        synchronized (lock) {
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
    }

    /**
     * The items that can be previewed in a domain's creative tab: every registered item the domain's
     * tag claims. There is no per-domain branch because there is no per-domain membership rule — the
     * tag is the whole of it.
     */
    @Override
    public List<Item> collectPreviewItems(final TierDomain domain) {
        synchronized (lock) {
            return registeredItemsFor(domain);
        }
    }

    @Override
    public List<TierDefinition> getOrderedValidTiersForItem(final Item item, final TierDomain domain) {
        synchronized (lock) {
            if (!this.inferDomains(item).contains(domain)) {
                return List.of();
            }
            return this.getOrderedTiers(domain);
        }
    }

    public List<TierDefinition> getOrderedTiers(final TierDomain domain) {
        synchronized (lock) {
            return List.copyOf(this.tiersByDomain.getOrDefault(domain, List.of()));
        }
    }

    /**
     * Domain-scoped lookup. This is the only correct way to resolve a tier id, because tier ids are
     * shared across domains.
     */
    public Optional<TierDefinition> getTier(final ResourceLocation tierId, final TierDomain domain) {
        synchronized (lock) {
            if (tierId == null || domain == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(this.tiersByKey.get(new TierKey(domain, tierId)));
        }
    }

    /**
     * Unscoped lookup kept for API compatibility. Ambiguous whenever more than one domain declares
     * the id, so it returns empty in that case rather than an arbitrary winner.
     */
    @Override
    public Optional<TierDefinition> getTier(final ResourceLocation tierId) {
        synchronized (lock) {
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
    }

    /**
     * Deliberately absent rather than defaulted.
     *
     * <p>Every item in a domain tag used to read as that domain's first rung, because
     * {@code resolveCurrentTier} fell back to {@code tiers.get(0)} when nothing was recorded. That
     * made "untiered" unobservable: an item whose tier another mod had stripped still claimed to be
     * common, and the smithing table could not tell a fresh item from an upgraded one.</p>
     *
     * <p>An item is now untiered until something says otherwise. Untiered gear scales nothing, shows
     * no bracket and costs copper - or whatever the first rung's {@code upgrade_ingredient} names -
     * to join the ladder, which is what puts the bootstrap transition in
     * {@code TierBootstrapRecipe} in reach of a real failure rather than a theoretical one.</p>
     */
    public Optional<TierDefinition> getInitialTierForItem(final Item item, final TierDomain preferredDomain) {
        synchronized (lock) {
            return Optional.empty();
        }
    }

    public Optional<TierDefinition> getPreviousTier(final TierDefinition tier) {
        synchronized (lock) {
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
    }


    public Set<TierDomain> resolveDomains(final Item item) {
        synchronized (lock) {
            return this.inferDomains(item);
        }
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
    public Set<TierDomain> inferDomains(final Item item) {
        synchronized (lock) {
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
    }

    public Map<String, Attribute> getScalableAttributes(final Item item) {
        synchronized (lock) {
            final Map<String, Attribute> keys = new LinkedHashMap<>();
            collectAttributeMap(item, EquipmentSlot.MAINHAND, keys);
            collectAttributeMap(item, EquipmentSlot.HEAD, keys);
            collectAttributeMap(item, EquipmentSlot.CHEST, keys);
            collectAttributeMap(item, EquipmentSlot.LEGS, keys);
            collectAttributeMap(item, EquipmentSlot.FEET, keys);
            return Map.copyOf(keys);
        }
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

    /**
     * The strongest scalar multiplier any of this stack's ladders declares for {@code key}.
     *
     * <p>An axe climbs both the weapon and the tool ladder, and both may declare a {@code durability}
     * multiplier. Applying whichever one was iterated first would make the answer depend on map
     * order, so the one furthest from {@code 1.0} wins: that is the ladder that actually changed the
     * item, and the other is agreeing it did nothing.</p>
     */
    public Optional<Double> getScalarMultiplier(final ItemStack stack, final String key) {
        synchronized (lock) {
            return strongest(this.readAllTiersFromNbt(stack).values(),
                    tier -> this.getScalarMultiplier(tier, key));
        }
    }

    /**
     * The strongest attribute multiplier any of this stack's ladders declares for {@code attribute}.
     *
     * <p>The same reasoning as {@link #getScalarMultiplier(ItemStack, String)}, applied to attributes
     * — a tier's contract states what scales and how, and where two ladders both state it, the one
     * with an actual effect is the one worth showing.</p>
     */
    public Optional<Double> getAttributeMultiplier(final ItemStack stack, final Attribute attribute) {
        synchronized (lock) {
            return strongest(this.readAllTiersFromNbt(stack).values(),
                    tier -> this.getAttributeMultiplier(tier, attribute));
        }
    }

    private static Optional<Double> strongest(final Collection<TierDefinition> tiers,
                                              final java.util.function.Function<TierDefinition, Optional<Double>> lookup) {
        Optional<Double> best = Optional.empty();
        for (final TierDefinition tier : tiers) {
            final Optional<Double> candidate = lookup.apply(tier);
            if (candidate.isEmpty()) {
                continue;
            }
            if (best.isEmpty() || Math.abs(candidate.get() - 1.0D) > Math.abs(best.get() - 1.0D)) {
                best = candidate;
            }
        }
        return best;
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

    public List<TierDefinition> getAllTiers() {
        synchronized (lock) {
            return List.copyOf(this.tiersByKey.values());
        }
    }

    /**
     * The tier an item should be presented as, when no domain is specified.
     *
     * <p>Every domain the item belongs to has its own ladder, so an axe can be {@code epic} as a
     * weapon and {@code common} as a tool. The name line has one slot for one bracket, so it shows
     * the highest rung the item has reached on any ladder: that is the state a player who has been
     * upgrading the item expects to see, and hiding it behind the lower ladder would read as a bug.
     * Ties fall back to domain declaration order, which is stable between runs.</p>
     */
    public Optional<TierDefinition> resolveCurrentTier(final ItemStack stack, final TierDomain preferredDomain) {
        synchronized (lock) {
            if (preferredDomain != null) {
                return this.readTierFromNbt(stack, preferredDomain);
            }
            final Map<TierDomain, TierDefinition> stored = this.readAllTiersFromNbt(stack);
            return stored.isEmpty()
                    ? Optional.empty()
                    : stored.values().stream().max(this.displayTierComparator());
        }
    }

    /**
     * Orders tiers for display: highest rung first, with domain declaration order breaking ties.
     *
     * <p>{@code sort_order} is the ladder's own statement of rank, so it beats everything else.
     * Domain order is the tie-break because an axe's {@code uncommon} weapon rung and {@code uncommon}
     * tool rung are equally good, and the answer must not change between runs.</p>
     */
    private Comparator<TierDefinition> displayTierComparator() {
        return Comparator.comparingInt(TierDefinition::sortOrder)
                .thenComparingInt(tier -> tier.domain().ordinal());
    }

    /**
     * Reads every ladder this stack carries, keyed by domain.
     *
     * <p>Ladder state lives under {@code TieredEchoes.domains.<domain>} so that an item in two tags
     * sits on two ladders independently — an axe is a weapon and a tool, and neither rung may stand
     * in for the other.</p>
     */
    public Map<TierDomain, TierDefinition> readAllTiersFromNbt(final ItemStack stack) {
        synchronized (lock) {
            final LinkedHashMap<TierDomain, TierDefinition> result = new LinkedHashMap<>();
            final CompoundTag te = stackTe(stack);
            if (te == null || !te.contains(DOMAINS_TAG)) {
                return result;
            }
            final CompoundTag domains = te.getCompound(DOMAINS_TAG);
            for (final String key : domains.getAllKeys()) {
                final TierDomain domain = TierDomain.byName(key).orElse(null);
                if (domain == null) {
                    // A domain a datapack removed, or one written by a newer build. Skipping it leaves
                    // the rest of the item's ladders intact instead of discarding the whole stack.
                    continue;
                }
                final ResourceLocation tierId = ResourceLocation.tryParse(domains.getCompound(key).getString(TIER_ID_TAG));
                if (tierId == null) {
                    continue;
                }
                this.getTier(tierId, domain).ifPresent(tier -> result.put(domain, tier));
            }
            return result;
        }
    }

    /**
     * The ladders this stack has any recorded state on, whether or not that state names a rung.
     *
     * <p>A ladder compound may exist without naming a rung, and it still counts: it is a ladder the
     * item is on, it simply has not been raised to a rung yet. Iteration follows enum declaration
     * order so the result is deterministic.</p>
     */
    public List<TierDomain> recordedLadderDomains(final ItemStack stack) {
        synchronized (lock) {
            final CompoundTag te = stackTe(stack);
            if (te == null || !te.contains(DOMAINS_TAG)) {
                return List.of();
            }
            final CompoundTag domains = te.getCompound(DOMAINS_TAG);
            final List<TierDomain> found = new ArrayList<>();
            for (final TierDomain domain : TierDomain.values()) {
                if (domains.contains(domain.getSerializedName())) {
                    found.add(domain);
                }
            }
            return List.copyOf(found);
        }
    }

    /**
     * Reads the tier recorded for one specific domain, or empty when that ladder has no rung stored.
     */
    public Optional<TierDefinition> readTierFromNbt(final ItemStack stack, final TierDomain domain) {
        synchronized (lock) {
            final CompoundTag te = stackTe(stack);
            if (te == null || !te.contains(DOMAINS_TAG) || domain == null) {
                return Optional.empty();
            }
            final String key = domain.getSerializedName();
            final CompoundTag domains = te.getCompound(DOMAINS_TAG);
            if (!domains.contains(key)) {
                return Optional.empty();
            }
            final ResourceLocation tierId = ResourceLocation.tryParse(domains.getCompound(key).getString(TIER_ID_TAG));
            if (tierId == null) {
                return Optional.empty();
            }
            return this.getTier(tierId, domain);
        }
    }

    /**
     * The mod's own tag on a stack, or null when there is nothing to read.
     *
     * <p>Every read of tier state goes through here, which is what makes {@code tier_system} a
     * single switch: when it is off, this reports "no tag" and the rung a stack carries simply does
     * not exist as far as scaling, names, rarity and ladder membership are concerned. The tag itself
     * is untouched — switching a system off makes the game forget a thing exists, not destroy it,
     * so turning it back on recovers what was there.</p>
     *
     * <p>Deliberately not on the write path: {@code UpgradeService} creates the tag directly, so an
     * operator can disable tiers for a world and still have items that remember their rungs.</p>
     */
    private static CompoundTag stackTe(final ItemStack stack) {
        if (!PolicyService.getInstance().get().tierSystem()) {
            return null;
        }
        if (stack.isEmpty() || !stack.hasTag() || !stack.getTag().contains(ROOT_TAG)) {
            return null;
        }
        return stack.getTag().getCompound(ROOT_TAG);
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
