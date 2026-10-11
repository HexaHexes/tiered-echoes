package org.bitwisemadness.tieredechoes.testutil;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.bitwisemadness.tieredechoes.common.registry.TieredEchoesTagKeys;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Binds items into a domain's item tag, so tests can exercise domain inference the way the game
 * actually does it.
 *
 * <p>{@code inferDomains} is tag-driven, and a tag only exists once a datapack has been loaded —
 * {@code Bootstrap.bootStrap()} fills the registries but leaves every tag empty. A test that builds
 * an {@code ItemStack} and asks which domain it belongs to would therefore always get "none", which
 * is correct behaviour reading like a bug. Binding here closes that gap without pretending that
 * membership is decided by item class, which is the thing this changed away from.</p>
 *
 * <p>Registration is cumulative and rebinds the whole registry each time, because
 * {@code Registry#bindTags} replaces the tag set rather than merging into it.</p>
 */
public final class TestTags {

    private static final Map<TierDomain, List<Item>> BOUND = new EnumMap<>(TierDomain.class);
    private static boolean dirty;

    private TestTags() {
    }

    /** Puts these items in the domain's tag and makes the binding visible to inference. */
    public static synchronized void bindDomain(final TierDomain domain, final Item... items) {
        TestBootstrap.init();
        final List<Item> list = BOUND.computeIfAbsent(domain, key -> new ArrayList<>());
        for (final Item item : items) {
            if (!list.contains(item)) {
                list.add(item);
            }
        }
        dirty = true;
        rebind();
    }

    /** Convenience for the common case of one item, e.g. {@code bindWeapon(Items.IRON_SWORD)}. */
    public static void bindWeapon(final Item... items) {
        bindDomain(TierDomain.WEAPON, items);
    }

    /** Drops every binding, so a test that asserts on an untagged item is not fooled by an earlier one. */
    public static synchronized void clear() {
        BOUND.clear();
        dirty = true;
        rebind();
    }

    private static void rebind() {
        if (!dirty) {
            return;
        }
        final Map<TagKey<Item>, List<Holder<Item>>> tags = new java.util.LinkedHashMap<>();
        for (final Map.Entry<TierDomain, List<Item>> entry : BOUND.entrySet()) {
            TieredEchoesTagKeys.forDomain(entry.getKey()).ifPresent(tag -> {
                final List<Holder<Item>> holders = new ArrayList<>();
                for (final Item item : entry.getValue()) {
                    holders.add(BuiltInRegistries.ITEM.wrapAsHolder(item));
                }
                tags.put(tag, List.copyOf(holders));
            });
        }
        // AIR is the registry default; binding an empty tag set must not leave it claiming a domain.
        BuiltInRegistries.ITEM.bindTags(tags);
        dirty = false;
    }

    /** The vanilla iron sword bound as a weapon — the shape most inference tests want. */
    public static void bindVanillaWeaponDefaults() {
        bindDomain(TierDomain.WEAPON, Items.IRON_SWORD);
    }
}
